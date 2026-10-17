import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import time

payload = json.load(sys.stdin)
project = Path('/tmp/project')
project.mkdir()
for file in payload['files']:
    target = project / file['path']
    # Defense in depth: the API already validates Windows-compatible relative paths.
    if target.resolve().is_relative_to(project) is False:
        raise ValueError('unsafe project path')
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(file['content'], encoding='utf-8', newline='')
env = dict(os.environ, HOME='/tmp', PYTHONDONTWRITEBYTECODE='1', PYTHONUNBUFFERED='1')
gui = payload['gui']
xserver = child = None
result = {'output': '', 'error': None, 'video': None}
launcher = "import runpy,sys,os;p,entry,inp=sys.argv[1:];sys.path[:0]=[os.path.dirname(entry),p];sys.argv=[entry];runpy.run_path(entry,run_name='__main__',init_globals={'input_text':inp})"
try:
    if gui:
        env['DISPLAY'] = ':99'
        env['SDL_AUDIODRIVER'] = 'dummy'
        xserver = subprocess.Popen(['Xvfb', ':99', '-screen', '0', '800x600x24', '-nolisten', 'tcp', '-ac'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=env)
        for _ in range(30):
            if Path('/tmp/.X11-unix/X99').exists():
                break
            time.sleep(.05)
        if xserver.poll() is not None:
            raise RuntimeError('Xvfb could not start')
    with open('/tmp/stdout', 'wb') as out, open('/tmp/stderr', 'wb') as err:
        child = subprocess.Popen([sys.executable, '-I', '-u', '-c', launcher, str(project), str(project / payload['entry']), payload['input']], cwd=project, env=env, stdout=out, stderr=err)
        if gui:
            time.sleep(.4)
            capture = subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-f', 'x11grab', '-video_size', '800x600', '-framerate', '8', '-i', ':99', '-t', '4', '-an', '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '32', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-y', '/tmp/preview.mp4'], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=10)
            if capture.returncode:
                raise RuntimeError('Desktop recording failed')
            video = Path('/tmp/preview.mp4').read_bytes()
            if len(video) > 1_048_576 or len(video) < 1000 or b'ftyp' not in video[:32]:
                raise RuntimeError('Desktop recording invalid or exceeds 1 MiB')
            result['video'] = base64.b64encode(video).decode('ascii')
            if child.poll() is None:
                # Recording completion is an intentional stop, not an application timeout.
                # SDL may consume SIGTERM without exiting when its event loop ignores QUIT.
                child.kill()
                child.wait()
            elif child.returncode:
                result['error'] = 'Python 桌面程序运行失败，请查看错误输出'
        else:
            child.wait(timeout=8)
            if child.returncode:
                result['error'] = 'Python 程序运行失败，请查看错误输出'
    text = Path('/tmp/stdout').read_bytes() + Path('/tmp/stderr').read_bytes()
    if len(text) > 65536:
        result['error'] = 'Python 输出超过 64 KiB'
    result['output'] = text[:65536].decode('utf-8', errors='replace')
except subprocess.TimeoutExpired:
    result['error'] = 'Python 运行超时，已终止'
except Exception as failure:
    result['error'] = str(failure)[:500]
finally:
    for process in (child, xserver):
        if process is not None and process.poll() is None:
            process.kill()
            process.wait()
print(json.dumps(result, ensure_ascii=True))
