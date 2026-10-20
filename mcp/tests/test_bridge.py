import io
import json
import pathlib
import sys
import tempfile
import unittest
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from ochd_mcp import Bridge, serve, TOOLS

class FakeBridge(Bridge):
    def __init__(self): self.calls=[]; self.operations=[]
    def rpc(self, req): self.calls.append(req); return {"received": req}
    def agent(self, computer, op, args, wait=True):
        self.operations.append((computer, op, args, wait))
        if op == "write" and args["phase"] == "begin": return {"upload": "u"}
        if op == "read": return {"hex": "e4b8ad00ff"}
        return {"ok": True}

class BridgeTest(unittest.TestCase):
    def test_protocol(self):
        requests=[{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}},
                  {"jsonrpc":"2.0","method":"notifications/initialized"},
                  {"jsonrpc":"2.0","id":2,"method":"tools/list"},
                  {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"oc_list","arguments":{}}}]
        src=io.BytesIO(("\n".join(map(json.dumps,requests))+"\n").encode());out=io.StringIO()
        serve(FakeBridge(),src,out);results=[json.loads(s) for s in out.getvalue().splitlines()]
        self.assertEqual(len(results),3);self.assertEqual(results[0]["result"]["protocolVersion"],"2025-06-18")
        self.assertEqual(len(results[1]["result"]["tools"]),len(TOOLS));self.assertFalse(results[2]["result"]["isError"])
    def test_reject_bad_arguments(self):
        b=FakeBridge()
        for name,args in [("unknown",{}),("oc_power",{"computer":"c","action":"restart_server"}),("oc_read",{"computer":"c","path":"/log","count":99999}),("oc_list",{"token":"x"})]:
            with self.assertRaises(ValueError): b.call(name,args)
        self.assertEqual(b.calls,[])
    def test_write_chunks_and_expected_version(self):
        b=FakeBridge();text="中"*9000
        b.call("oc_write",{"computer":"c","path":"/test","text":text,"expected":"old"})
        self.assertEqual(b.operations[0][2]["expected"],"old")
        chunks=[x[2] for x in b.operations if x[2].get("phase")=="chunk"]
        self.assertEqual(b"".join(bytes.fromhex(x["hex"]) for x in chunks),text.encode())
        self.assertEqual([x["offset"] for x in chunks],[0,8192,16384,24576])
        self.assertEqual(b.operations[-1][2]["phase"],"commit")
    def test_no_commit_after_chunk_failure(self):
        b=FakeBridge();orig=b.agent
        def fail(c,o,a,wait=True):
            if a.get("phase")=="chunk": raise OSError("disconnected")
            return orig(c,o,a,wait)
        b.agent=fail
        with self.assertRaises(OSError): b.call("oc_write",{"computer":"c","path":"/test","text":"hello","expected":"missing"})
        self.assertFalse(any(x[2].get("phase")=="commit" for x in b.operations))
    def test_async_run(self):
        b=FakeBridge();b.call("oc_run",{"computer":"c","command":"ls"})
        self.assertFalse(b.operations[-1][3])
    def test_read_keeps_lossless_hex(self):
        result=FakeBridge().call("oc_read",{"computer":"c","path":"/log"})
        self.assertEqual(result["hex"],"e4b8ad00ff");self.assertTrue(result["text"].startswith("中"))
    def test_remote_config_rejected_before_connect(self):
        with tempfile.TemporaryDirectory() as d:
            p=pathlib.Path(d)/"config.json"
            for url in ["https://example.com/rpc","http://localhost:123/rpc","http://127.0.0.1:123/rpc?x=1","http://user:pass@127.0.0.1:123/rpc"]:
                p.write_text(json.dumps({"url":url,"token":"secret"}))
                with self.assertRaises(ValueError):Bridge(p).rpc({"op":"list"})
    def test_protocol_not_initialized(self):
        out=io.StringIO();serve(FakeBridge(),io.BytesIO(b'{"jsonrpc":"2.0","id":1,"method":"tools/list"}\n'),out)
        self.assertIn("error",json.loads(out.getvalue()))

if __name__=="__main__": unittest.main()
