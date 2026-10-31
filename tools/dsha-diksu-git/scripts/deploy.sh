#!/usr/bin/env bash
# 把仓库里的插件同步到 DSH 真正加载的目录。
#
# 背景：profile 里是 `"dsha-diksu-git": "link:/root/dsha-diksu-git"`，而 /root 下是
# 一份**独立拷贝**（/sdcard 是 fuse 文件系统，不能在仓库里建 node_modules 软链，
# 也不能把 /root 改成指回仓库的软链 —— Node 会按真实路径解析依赖，那样会找不到
# @deepseek-ai/dsh-tools）。所以仓库是源码，/root 是部署产物，用这个脚本同步。
#
#   tools/dsha-diksu-git/scripts/deploy.sh            # 默认同步到 /root/dsha-diksu-git
#   tools/dsha-diksu-git/scripts/deploy.sh /some/dir  # 或指定目录
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DST="${1:-/root/dsha-diksu-git}"

if [ ! -d "$DST" ]; then
  mkdir -p "$DST"
  printf '{\n  "name": "dsha-diksu-git",\n  "version": "0.0.0",\n  "type": "module",\n  "main": "lib/index.js"\n}\n' >"$DST/package.json"
fi

if [ ! -e "$DST/node_modules" ]; then
  DSH_MODULES="/usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules"
  if [ -d "$DSH_MODULES" ]; then
    ln -sfn "$DSH_MODULES" "$DST/node_modules"
  else
    echo "警告：找不到 $DSH_MODULES，插件将无法 import @deepseek-ai/dsh-tools" >&2
  fi
fi

# 只覆盖源码与元数据，保留 node_modules
for entry in lib test package.json README.md cordis.patch.yml; do
  if [ -e "$SRC/$entry" ]; then
    rm -rf "$DST/$entry"
    cp -a "$SRC/$entry" "$DST/$entry"
  fi
done

echo "已同步 $SRC → $DST"
echo "验证：cd $DST && node test/run-tests.mjs && node test/smoke-registry.mjs"
