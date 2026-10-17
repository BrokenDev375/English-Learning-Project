"""Verify the generated dataset, manifests, schemas and literal listening-fill evidence offline."""
import csv
import hashlib
import json
from pathlib import Path
import re
import unicodedata
import warnings
from collections import Counter
warnings.filterwarnings('ignore',category=DeprecationWarning)
from jsonschema import Draft202012Validator, RefResolver

root=Path(__file__).resolve().parents[1]
resource=root/'source/server/src/main/resources/demo'
dataset=json.loads((resource/'curriculum-v1.json').read_text(encoding='utf-8'))
spec=json.loads((root/'source/common/openapi-m02-m03.json').read_text(encoding='utf-8'))
resolver=RefResolver.from_schema(spec)
def schema(name,data): Draft202012Validator({'$ref':'#/components/schemas/'+name},resolver=resolver).validate(data)
qrows=list(csv.DictReader((root/'statics/dataset/m02-question-manifest.csv').open(encoding='utf-8-sig',newline='')))
mrows=list(csv.DictReader((root/'statics/dataset/m02-material-manifest.csv').open(encoding='utf-8-sig',newline='')))
assert len(qrows)==90 and len(mrows)==13 and len(dataset['lessons'])==6
materials={m['seedKey']:m for m in dataset['materials']}
assert len(materials)==13
for m in materials.values():
    if m['file']:
        content=(resource/'materials'/m['file']).read_bytes()
        assert 0<len(content)<=5242880 and hashlib.sha256(content).hexdigest()==m['sha256']
        assert next(row for row in mrows if row['seedKey']==m['seedKey'])['sha256']==m['sha256']
    body=dict(m['data'],requestId='00000000-0000-0000-0000-000000000001')
    if m['file']: body['assetId']=1
    schema('MaterialCreate',body)
allq=[q for lesson in dataset['lessons'] for q in lesson['questions']]
assert len({q['seedKey'] for q in allq})==90 and len({q['data']['prompt'] for q in allq})==90
assert Counter(q['practiceRole'] for q in allq)=={'GUIDED':30,'INDEPENDENT':30,'REVIEW_VARIANT':30}
assert Counter(q['data']['type'] for q in allq)=={'SINGLE_CHOICE':30,'FILL_BLANK':30,'MATCHING':30}
assert set(q['seedKey'] for q in allq)==set(row['seedKey'] for row in qrows)
assert sorted(Counter(l['unitSeedKey'] for l in dataset['lessons']).values())==[2,2,2]
audio_fills=0
for lesson in dataset['lessons']:
    assert len(lesson['questions'])==15
    for role in ['GUIDED','INDEPENDENT','REVIEW_VARIANT']:
        assert sorted(q['position'] for q in lesson['questions'] if q['practiceRole']==role)==list(range(5))
    body=dict(lesson['data'],requestId='00000000-0000-0000-0000-000000000001',unitId=1,unitBaseRevision=0,objectiveIds=[1],questions=[{'questionVersionId':i+1,'practiceRole':q['practiceRole'],'position':q['position']} for i,q in enumerate(lesson['questions'])])
    schema('LessonCreate',body)
    for q in lesson['questions']:
        d=q['data'];schema('QuestionCreate',dict(d,requestId='00000000-0000-0000-0000-000000000001',topicId=1,objectiveId=1,materialVersionId=1 if q['materialKey'] else None))
        row=next(row for row in qrows if row['seedKey']==q['seedKey'])
        assert row['lessonSeedKey']==lesson['seedKey'] and row['materialSeedKey']==(q['materialKey'] or '') and row['objectiveCode']==lesson['objectiveCode']
        if d['type']=='FILL_BLANK':
            for blank in d['answerKey']['blanks']:
                def normalize(text):
                    text=unicodedata.normalize('NFC',text)
                    if d['answerKey']['normalizeWhitespace']: text=re.sub(r'[ \t\r\n]+',' ',text).strip(' ')
                    return text if d['answerKey']['caseSensitive'] else text.lower()
                assert len({normalize(a) for a in blank['acceptedAnswers']})==len(blank['acceptedAnswers'])
                if q['materialKey'] and materials[q['materialKey']]['data']['kind']=='AUDIO':
                    transcript=materials[q['materialKey']]['data']['transcript'].lower()
                    assert any(a.lower() in transcript for a in blank['acceptedAnswers']),(q['seedKey'],'answer absent from transcript')
                    audio_fills+=1
assert audio_fills>=12
print('PASS: 6 lessons, 90 unique questions, 5/5/5 groups, 30/type, 13 materials; schemas, CSV edges, file hashes and '+str(audio_fills)+' listening-fill answers verified.')
