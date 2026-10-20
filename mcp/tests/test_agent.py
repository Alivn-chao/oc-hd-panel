import json
import pathlib
import unittest
from lupa import LuaRuntime
ROOT=pathlib.Path(__file__).resolve().parents[2]
class AgentTest(unittest.TestCase):
    def setUp(self):
        self.lua=LuaRuntime(unpack_returned_tuples=True)
        self.lua.execute((pathlib.Path(__file__).parent/"agent_harness.lua").read_text())
        self.lua.execute((ROOT/"src/main/resources/assets/ochdpanel/mcp/99_ochd_mcp.lua").read_text())
        self.lua.globals()._init();self.seq=0
    def call(self,op,**args):
        self.seq+=1;raw=self.lua.globals().submit(str(self.seq),op,self.lua.table_from(args));return json.loads(raw)
    def test_read_binary_and_json_escaping(self):
        r=self.call("read",path="/log");self.assertTrue(r["ok"])
        self.assertEqual(bytes.fromhex(r["value"]["hex"]),b'hello\n"quoted"\\line\x00')
    def test_traversal_and_chunk_limits(self):
        for p in ["/../secrets","/a/../../b","C:\\secret"]:
            self.assertFalse(self.call("read",path=p)["ok"])
        self.assertFalse(self.call("read",path="/log",count=8193)["ok"])
    def test_write_backup(self):
        token=self.call("read",path="/program.lua")["value"]["version"]
        upload=self.call("write",phase="begin",path="/program.lua",expected=token)["value"]["upload"]
        self.assertTrue(self.call("write",phase="chunk",upload=upload,offset=0,hex=b"new program".hex())["ok"])
        result=self.call("write",phase="commit",upload=upload)["value"]
        self.assertEqual(self.lua.globals().getFile("/program.lua"),"new program")
        self.assertEqual(self.lua.globals().getFile(result["backup"]),"old program")
    def test_boot_script_backups_are_inside_subdirectory(self):
        upload=self.call("write",phase="begin",path="/boot/test.lua",expected="missing")["value"]["upload"]
        self.call("write",phase="chunk",upload=upload,offset=0,hex=b"print(1)".hex())
        self.call("write",phase="commit",upload=upload)
        token=self.call("read",path="/boot/test.lua")["value"]["version"]
        upload=self.call("write",phase="begin",path="/boot/test.lua",expected=token)["value"]["upload"]
        r=self.call("write",phase="commit",upload=upload)["value"]
        self.assertTrue(r["backup"].startswith("/boot/.ochd-mcp/"))
    def test_concurrent_edit_blocks_commit(self):
        token=self.call("read",path="/program.lua")["value"]["version"]
        upload=self.call("write",phase="begin",path="/program.lua",expected=token)["value"]["upload"]
        self.lua.globals().putFile("/program.lua","someone edited")
        self.assertFalse(self.call("write",phase="commit",upload=upload)["ok"])
        self.assertEqual(self.lua.globals().getFile("/program.lua"),"someone edited")
    def test_wrong_chunk_offset_rejected(self):
        upload=self.call("write",phase="begin",path="/new",expected="missing")["value"]["upload"]
        self.assertFalse(self.call("write",phase="chunk",upload=upload,offset=5,hex="00")["ok"])
    def test_capture_and_limit_output(self):
        r=self.call("run",command="echo hello");self.assertTrue(r["ok"]);self.assertIn("stderr",r["value"]["output"])
        r=self.call("run",command="large");self.assertEqual(len(r["value"]["output"]),16384);self.assertTrue(r["value"]["truncated"])
    def test_command_timeout(self):
        r=self.call("run",command="wait",timeout=1);self.assertFalse(r["ok"]);self.assertIn("timed out",r["error"])
    def test_runtime(self):
        r=self.call("runtime")["value"];self.assertEqual(r["freeMemory"],500);self.assertEqual(r["mounts"]["1"]["path"],"/")
    def test_first_page_only_calculates_version(self):
        r=self.call("read",path="/log",offset=1)["value"];self.assertNotIn("version",r)
if __name__=="__main__": unittest.main()
