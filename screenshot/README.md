# screenshot/

本地留存的模拟器冒烟截图，**不提交到仓库**（`.gitignore` 里排除了本目录除本 README 外的一切内容）。

## 为什么要有这个目录

冒烟测试的像素检查只能证明「屏幕上有东西被画出来了」，它判不了布局对不对。最典型的一种失败是静默的：`archivePainterOrPlaceholder` 在 XML vector 解析失败时返回 1×1 透明画笔，界面照样「有内容」、检查照样通过，只有人眼看图才能发现纹饰根本没画出来。

而 GitHub Actions 的产物保留期很短——不到三周的 run 下载就会返回 `410 Artifact has expired`。所以每次跑完冒烟测试要尽快把图抓下来。

## 用法

```bash
# 抓当前分支最新一次 run
.github/scripts/fetch-smoke-screenshots.sh

# 抓指定的 run
.github/scripts/fetch-smoke-screenshots.sh 39979857765

# 看本地已保留哪些
.github/scripts/fetch-smoke-screenshots.sh --list
```

Windows 上用 Git bash 执行：

```bash
"C:/Program Files/Git/bin/bash.exe" .github/scripts/fetch-smoke-screenshots.sh
```

## 保留策略

目录按 `screenshot/<runId>-<shortSha>/` 存放，每次下载成功后只保留**最新两次**，更早的自动删除（可用 `--keep N` 改）。

按 run id 数值排序而不是按文件时间：run id 单调递增，等价于「按抓取时间」，而且不依赖复制或检出过程是否保留了时间戳。

每次归档只保留这些文件：`*.png`、`summary.md`、`screenshot-checks.txt`、`crashes.txt`、`app-log-*.txt`、`console-*.log`。xcodebuild 日志不保留——体积大，而且随时能在 run 页面重新看到。

## 失败时也可以抓

run 失败时产物的价值通常更高，脚本对红色 run 只提示不拒绝。产物真的过期了才会报错，那时候要么重跑工作流，要么把 `--keep` 保留下来的旧归档翻出来看。
