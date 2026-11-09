from pathlib import Path
import json,re,sqlite3

root=Path(__file__).resolve().parent.parent
project=root
schemas=project/'app/schemas/com.qingji.ledger.LedgerDatabase'
safe=schemas
v1=json.loads((schemas/'1.json').read_text(encoding='utf8'))['database']
v2=json.loads((safe/'2.json').read_text(encoding='utf8'))['database']
source=(project/'app/src/main/java/com/qingji/ledger/Database.kt').read_text(encoding='utf8')
statements=re.findall(r'db\.execSQL\("([^"\n]*)"\)',source)
assert len(statements)==11,statements
def create(db,schema):
    for entity in schema['entities']:
        name=entity['tableName']
        db.execute(entity['createSql'].replace('${TABLE_NAME}',name))
        for index in entity.get('indices',[]):db.execute(index['createSql'].replace('${TABLE_NAME}',name))
def structure(db,name):
    columns=[(r[1],r[2],r[3],r[4],r[5]) for r in db.execute('PRAGMA table_info("'+name+'")')]
    indices={r[1]:(r[2],tuple(i[2] for i in db.execute('PRAGMA index_info("'+r[1]+'")'))) for r in db.execute('PRAGMA index_list("'+name+'")')}
    return columns,indices
db=sqlite3.connect(':memory:');create(db,v1)
db.execute("INSERT INTO entries (id,type,amount,categoryId,date,time,note,refundOf,zoneId,createdAt,updatedAt,deletedAt,revision,syncState) VALUES ('legacy','EXPENSE',3000,'expense-0','2026-11-01','12:00','old expense',NULL,'Asia/Shanghai',1,1,NULL,1,'LOCAL')")
db.execute("PRAGMA user_version=1")
with db:
    for statement in statements:db.execute(statement)
    db.execute('PRAGMA user_version=2')
assert db.execute("SELECT amount,assetId,affectsBalance FROM entries WHERE id='legacy'").fetchone()==(3000,None,0)
assert db.execute('SELECT COUNT(*) FROM asset_accounts').fetchone()[0]==0
fresh=sqlite3.connect(':memory:');create(fresh,v2)
for entity in v2['entities']:
    name=entity['tableName']
    assert structure(db,name)==structure(fresh,name),name+' migrated schema differs from Room v2 export'
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
result={'status':'passed','sql_statements':len(statements),'tables_validated':len(v2['entities']),'legacy_amount_preserved':True,'legacy_asset_id':None,'legacy_affects_balance':False,'new_accounts_invented':False,'integrity_check':'ok','limitation':'Desktop SQLite structural/data test; Android Room migration instrumentation is provided but not device-executed.'}
out=project/'verification/assets-migration.json';out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(result,indent=2),encoding='utf8')
print(json.dumps(result))
