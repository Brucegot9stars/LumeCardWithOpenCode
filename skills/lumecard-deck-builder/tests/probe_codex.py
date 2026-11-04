"""Optional Codex discovery probe; no model inference and no permanent installation."""
import json
import os
from pathlib import Path
import queue
import shutil
import subprocess
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[1]


def main():
    with tempfile.TemporaryDirectory(prefix="lumecard-codex-probe-") as tmp:
        project = Path(tmp) / "project"
        destination = project / ".agents/skills" / ROOT.name
        shutil.copytree(ROOT, destination, ignore=shutil.ignore_patterns("__pycache__", "dist"))
        # A temporary official tool home isolates probe state from the user's setup.
        tool_home = Path(tmp) / "codex-state"
        tool_home.mkdir()
        env = dict(os.environ, CODEX_HOME=str(tool_home))
        messages = queue.Queue()
        with tempfile.TemporaryFile(mode="w+t") as errors:
            proc = subprocess.Popen(["codex", "app-server", "--listen", "stdio://"],
                                    cwd=project, env=env, stdin=subprocess.PIPE,
                                    stdout=subprocess.PIPE, stderr=errors, text=True)
            def reader():
                for line in proc.stdout:
                    try:
                        messages.put(json.loads(line))
                    except ValueError:
                        pass
            threading.Thread(target=reader, daemon=True).start()
            def send(message):
                proc.stdin.write(json.dumps(message) + "\n")
                proc.stdin.flush()
            def receive(ident):
                for _ in range(100):
                    msg = messages.get(timeout=20)
                    if msg.get("id") == ident:
                        if "error" in msg:
                            raise RuntimeError(msg["error"])
                        return msg["result"]
                raise RuntimeError("response limit exceeded")
            try:
                send({"id": 1, "method": "initialize", "params": {"clientInfo": {"name": "lumecard_probe", "version": "0.1.0"}}})
                receive(1)
                send({"method": "initialized"})
                send({"id": 2, "method": "skills/list", "params": {"cwds": [str(project)], "forceReload": True}})
                result = receive(2)
                matches = [skill for row in result["data"] for skill in row["skills"] if skill["name"] == ROOT.name]
                if len(matches) != 1 or not matches[0].get("enabled", True):
                    raise RuntimeError("skill not discovered and enabled")
                print(json.dumps({"discovered": matches[0]["name"], "enabled": matches[0].get("enabled", True), "scope": matches[0].get("scope"), "modelInvocation": "not tested"}))
            finally:
                proc.terminate()
                try:
                    proc.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait()


if __name__ == "__main__":
    main()
