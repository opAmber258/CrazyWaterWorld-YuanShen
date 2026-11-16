import sys,json,os
from pathlib import Path
from bb_client import Client,structured
ROOT=Path(__file__).resolve().parent.parent
URL=os.environ.get('BLOCKBENCH_MCP_URL','http://[::1]:3005/bb-mcp')
def run(code):
 with Client(URL,timeout=90) as c:return structured(c.tool('risky_eval',{'code':code}))
if __name__=='__main__':
 if sys.argv[1]=='eval':print(json.dumps(run(Path(sys.argv[2]).read_text(encoding='utf8')),ensure_ascii=True))
 elif sys.argv[1]=='tool':
  with Client(URL,timeout=90) as c:print(json.dumps(c.tool(sys.argv[2],json.loads(sys.argv[3]) if len(sys.argv)>3 else {}),ensure_ascii=True))
