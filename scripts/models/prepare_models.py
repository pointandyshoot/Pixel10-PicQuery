#!/usr/bin/env python3
"""Export, quantise, numerically validate and package MobileCLIP2-S0 research assets."""
from pathlib import Path
import gc, hashlib, importlib.util, json, shutil, subprocess, sys
import numpy as np
import torch
from ai_edge_litert.interpreter import Interpreter

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/model-export'
ASSETS = ROOT / 'pixel/src/main/assets'
torch.set_num_threads(2)
torch.set_grad_enabled(False)
subprocess.run([sys.executable, str(ROOT/'scripts/models/export_mobileclip2.py'), '--output-dir', str(OUT)], check=True)
subprocess.run([sys.executable, str(ROOT/'scripts/models/quantize_text.py'), '--asset-dir', str(OUT)], check=True)
spec=importlib.util.spec_from_file_location('exporter', ROOT/'scripts/models/export_mobileclip2.py')
exporter=importlib.util.module_from_spec(spec); spec.loader.exec_module(exporter)
model=exporter.create_model('MobileCLIP2-S0','dfndr2b')
import open_clip
texts=['a dog', 'red dirt road with camper beside a river', 'sunset over the ocean', 'Bramwell station sign',
       'Shannan swimming at a waterfall', 'café at Broome', 'a red bird', 'a blue car', 'a receipt', ' '.join(['photo']*150)]
tokens=open_clip.get_tokenizer('MobileCLIP2-S0')(texts).numpy().astype(np.int32)
images=np.random.default_rng(42).random((4,3,256,256),dtype=np.float32)
refs={'image': model.encode_image(torch.from_numpy(images),normalize=True).numpy(),
      'text':model.encode_text(torch.from_numpy(tokens),normalize=True).numpy()}
del model; gc.collect()
report={'model':'MobileCLIP2-S0', 'pretrained':'dfndr2b', 'purpose':'research proof of concept',
        'scope':'Numerical export/quantisation validation; not a retrieval-quality or Pixel performance benchmark.', 'cases':{}}
for tower,data,path in [('image',images,OUT/'image_model.tflite'), ('text',tokens,OUT/'text_model_dynamic_wi8.tflite')]:
    interpreter=Interpreter(model_path=str(path),num_threads=2);interpreter.allocate_tensors()
    inp=interpreter.get_input_details()[0];out=interpreter.get_output_details()[0]
    values=[]
    for row in data:
        interpreter.set_tensor(inp['index'],row[None]);interpreter.invoke();values.append(interpreter.get_tensor(out['index'])[0])
    pred=np.array(values); ref=refs[tower]
    sims=(pred*ref).sum(1)/(np.linalg.norm(pred,axis=1)*np.linalg.norm(ref,axis=1))
    assert np.isfinite(pred).all() and float(sims.min()) > (0.999 if tower=='image' else 0.98), (tower,sims)
    report['cases'][tower]={'minimum_cosine_vs_pytorch':float(sims.min()), 'max_abs_error':float(np.abs(pred-ref).max()),
        'sha256':hashlib.sha256(path.read_bytes()).hexdigest(), 'bytes':path.stat().st_size}
    print(tower, report['cases'][tower], flush=True)
    del interpreter;gc.collect()
# Only replace the APK assets after all validation succeeds. Never ship experimental or FP32 text duplicates.
ASSETS.mkdir(parents=True,exist_ok=True)
for f in ASSETS.glob('*.tflite'):f.unlink()
shutil.copy2(OUT/'image_model.tflite',ASSETS/'image_model.tflite')
shutil.copy2(OUT/'text_model_dynamic_wi8.tflite',ASSETS/'text_model.tflite')
identity='MobileCLIP2-S0:'+':'.join(report['cases'][k]['sha256'] for k in ['image','text'])
(ASSETS/'model-id.txt').write_text(identity+'\n')
(ASSETS/'model-manifest.json').write_text(json.dumps(report,indent=2)+'\n')
(ROOT/'docs').mkdir(exist_ok=True)
(ROOT/'docs/model-validation.json').write_text(json.dumps(report,indent=2)+'\n')
fixtures=[{'text':t,'tokens':v.tolist()} for t,v in zip(texts,tokens)]
(ROOT/'pixel/src/test/resources/tokenizer-fixtures.json').write_text(json.dumps(fixtures,ensure_ascii=False,indent=2)+'\n')
print('Validated model assets packaged.', flush=True)
