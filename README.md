
<center>

# Sesame-TK


![Sesame-TK](https://socialify.git.ci/Fansirsqi/Sesame-TK/image?description=1&font=Source%20Code%20Pro&forks=1&issues=1&logo=https%3A%2F%2Fraw.githubusercontent.com%2FFansirsqi%2FSesame-TK%2Frefs%2Fheads%2Fmain%2Fapp%2Fsrc%2Fmain%2Fassets%2Fweb%2FSesame-TK-logo.svg&name=1&owner=1&pattern=Circuit%20Board&pulls=1&stargazers=1&theme=Auto)


[![License](https://img.shields.io/github/license/Fansirsqi/Sesame-TK?labelColor=fff&label=License&logo=gnuprivacyguard)](https://raw.githubusercontent.com/Fansirsqi/Sesame-TK/refs/heads/main/LICENSE)
[![Latest Release](https://img.shields.io/github/release/Fansirsqi/Sesame-TK?labelColor=fff&label=Releases&logo=gitlfs)](../../releases)
[![All Releases Download](https://img.shields.io/github/downloads/Fansirsqi/Sesame-TK/total?labelColor=fff&label=Downloads&logo=codefresh)](../../releases)
[![Telegram](https://img.shields.io/badge/Sesame--TK-nul?&logo=Telegram&label=Telegram-Channel&labelColor=fff&link=https%3A%2F%2Ft.me%2Ffansirsqi_xposed_sesame)](https://t.me/fansirsqi_xposed_sesame)



## 本人不是专业的开发者，仅仅是一名热爱开源的爱好者，欢迎大家提出宝贵意见，共同完善此项目。

</center>

代码的大部分内容是通过 `GPT-4o` 模型的辅助来完成的。

`bug`请[提交issues](https://github.com/Fansirsqi/Sesame-TK/issues/new/choose)
功能建议请提交PR

有相关问题，我也可能不会修复,大家轻喷，因为我不是专业的开发者，我可能真的不会修复。

访问异常请手动开启 平衡网络延迟选项，设置适当的延迟时间以及查询时间

对了，我自己用的支付宝版本是`10.5.88.8000`


如果你想自己编译，请fork本项目

然后在仓库设置相关签名文件信息，使用GitHub Actions编译，下载编译好的APK文件，安装到手机上即可
签名的生成以及转码请自行🔍解决 很简单滴~，你绝B可以


|仓库变量名| 变量值                  |
|----|----------------------|
|`ANDROID_SIGNING_KEY`| `keystore.jks`文件的base64编码字符串 |
|`ANDROID_KEY_ALIAS`| `keystore.jks`文件别名 |
|`ANDROID_SIGNING_PASSWORD`| `keystore.jks`文件密码 |
|`ANDROID_KEY_PASSWORD`| `keystore.jks`文件密码 |

设置好这些后，去仓库新建一个release，随便新建一个tag，然后点击`Publish release`，GitHub Actions会自动编译并发布APK文件到release中，下载安装即可



<details>
<summary>Preview Images</summary>

<div style="display: flex; align-items: flex-start; justify-content: center;">

  <img src="https://pic2.ziyuan.wang/user/fansir/2024/11/Screenshot_2024-11-20-19-40-19-594_fansirsqi.xposed.sesame-edit_66964347f6135.jpg" alt="Screenshot 1" style="max-width: 35%; height: auto; margin-right: 10px;">

  <img src="https://pic2.ziyuan.wang/user/fansir/2024/11/Screenshot_2024-11-20-19-40-36-528_fansirsqi.xposed.sesame_a545f9fee2510.jpg" alt="Screenshot 2" style="max-width: 35%; height: auto;">

</div>

</details>

<details> <summary>Archived content</summary> 


---

## [本仓库](https://github.com/TKaxv-7S/Sesame-TK) 已存档

新版本可前往由 [@lazy-immortal](https://github.com/lazy-immortal) 维护的 [Sesame](https://github.com/lazy-immortal/Sesame) 更新

<h1>🚨 为了大家的资金安全与个人信息安全，墙裂建议</h1>
<p>
  <strong style="color: red;">不要使用任何未开放源代码的修改版！</strong><br/>
  <strong style="color: red;">不要使用任何未开放源代码的修改版！</strong><br/>
  <strong style="color: red;">不要使用任何未开放源代码的修改版！</strong>
</p>

## 自北京时间2024年7月15日开始，开源协议已变更，该项目禁止用于任何商业用途，并禁止二次修改后闭源发布

# 从v1.3.0-TK版本开始使用新UI

## 感谢 [@wh-990624](https://github.com/wh-990624) 重构并开发新UI

## 感谢 ༒激༙྇流༙྇泉༙྇༒ 重新设计新UI

### 由于下游闭源项目违反本项目开源协议，从v1.3.0-TK版本开始，前端作者将闭源前端新UI源码，本仓库仅提交发布文件，后端暂不受影响

### 特别感谢这个项目的上一位维护者[@constanline](https://github.com/constanline)，以及更早的维护者[@pansong291](https://github.com/pansong291)与其他维护者们
### 如果您开发了新功能，觉得开发的功能还不错，同时愿意贡献PR，非常欢迎，也非常感谢大家为这个项目的付出！
### 注：该项目不支持合并任何 通过修改数据而实际获利 的功能PR

### 旧版本在 [XQuickEnergy](https://github.com/TKaxv-7S/XQuickEnergy)


</details>

## 主要功能
感谢蚂蚁森林对绿化事业的贡献。快速收取蚂蚁森林能量，也为祖国的绿化事业出一份微薄之力~

### 版本特点 By [@TKaxv-7S](https://github.com/TKaxv-7S)
1. 重构 系统架构，**功能与配置全部模块化**，以后添加功能**无需再开发配置页面，大幅降低开发门槛，并极大节省开发成本**，欢迎有兴趣的朋友参与开发，开发指南见[维基](https://github.com/TKaxv-7S/Sesame-TK/wiki)或如下图所示，非常欢迎大家提[PR](https://github.com/TKaxv-7S/Sesame-TK/pulls)
![Sesame-TK开发指南](https://github.com/TKaxv-7S/Sesame-TK/assets/22593101/4d8451fe-2b7f-4f19-9439-b0afbf683510)
2. 重构 **森林收能量代码**，大幅提升能量多的账号收取效率
3. 重构 配置模块，**所有配置需要重新配置**，新配置文件名称为**config_v2.json**，旧配置文件未删除，可作参考
4. 修改 配置界面，模块列表改为左侧垂直布局
5. 添加 定时唤醒与定时执行逻辑，在基础设置中可配置多个定时执行或定时唤醒时间
6. 修复 一些逻辑问题

## 使用说明

1. 本APP是为了学习研究用，不得进行任何形式的转发，发布，传播。
2. 请于24小时内卸载本APP。若使用期间造成任何损失，作者不负任何责任。
3. 本APP不篡改，不修改，不获取任何个人信息及其支付宝信息。
4. 本APP使用者因为违反本声明的规定而触犯中华人民共和国法律的，一切后果自负，作者不承担任何责任。
5. 凡以任何方式直接、间接使用APP者，视为自愿接受本声明的约束。
6. 本APP如无意中侵犯了某个媒体或个人的知识产权，请来信或来电告之，作者将立即删除。

## 授权说明
本项目fork自
基于 [constanline版XQuickEnergy](https://github.com/constanline/XQuickEnergy) 
与 [pansong291版XQuickEnergy](https://github.com/pansong291/XQuickEnergy) 
开发的项目[Sesame-TK](https://github.com/TKaxv-7S/Sesame-TK)
并且在其基础上进行了少量的功能改进与优化。得益于GPT4-o模型的强大能力使得本项目能有这么多提交
但是不确定是否是有效修改，请自行斟酌考虑使用。

所有图片由 ༒激༙྇流༙྇泉༙྇༒ 授权使用

## 协议说明

在 **北京时间2024年7月15日之前** 提交的所有代码 ，遵循 Apache-2.0 协议

自 **北京时间2024年7月15日开始** 提交的所有代码 ，遵循 GPLv3 协议，并禁止用于任何商业用途、禁止二次修改后闭源发布

## 鸣谢

<a href="https://github.com/Fansirsqi/Sesame-TK/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Fansirsqi/Sesame-TK" />
</a>

贡献名单使用 [contrib.rocks](https://contrib.rocks) 生成


## Star History

<a href="https://star-history.com/#Fansirsqi/Sesame-TK&Timeline">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=Fansirsqi/Sesame-TK&type=Timeline&theme=dark" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=Fansirsqi/Sesame-TK&type=Timeline" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=Fansirsqi/Sesame-TK&type=Timeline" />
 </picture>
</a>

