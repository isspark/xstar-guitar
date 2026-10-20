// Curated notation fixture. Every numbered line is one complete measure.
window.guitarTechniquesAlphaTex = String.raw`
\title "吉他谱技巧与节奏图鉴"
\subtitle "C 大调 · 标准调弦 · 逐小节验证"
\tempo 96
\track "Guitar" { instrument 25 }
\staff { tabs }
\tuning (E4 B3 G3 D3 A2 E2)
\ts (4 4)
\section "A 分解和弦 / 空弦"
:8 3.5 2.4 0.3 1.2 0.1 1.2 0.3 2.4 |
\section "B 和弦 / 扫弦 / 琶音"
:4 (0.1 1.2 0.3 2.4 3.5){bd} (0.1 1.2 0.3 2.4 3.5){bu} (0.1 1.2 0.3 2.4 3.5){ad} (0.1 1.2 0.3 2.4 3.5){au} |
\section "C 附点 / 切分 / 延音线"
3.2.8{d} 5.2.16 5.2.8 -.2.8 3.2.4 1.2.4 |
\section "D 十六分 / 三十二分 / 休止"
:16 0.1 1.1 3.1 1.1 :32 0.1 1.1 3.1 1.1 0.1 1.1 3.1 1.1 :4 r 0.1 |
\section "E 三连音"
:8 0.1{tu 3} 1.1{tu 3} 3.1{tu 3} 3.1{tu 3} 1.1{tu 3} 0.1{tu 3} :4 1.1 0.1 |
\ts (6 8) \section "F 六八拍"
:8 0.5 2.4 2.3 1.2 0.1 1.2 |
\ts (3 4) \section "G 三四拍 / 二分音符"
:2 3.5 :4 0.3 |
\ts (4 4) \section "H 击弦 / 勾弦"
:4 5.3{h} 7.3 7.3{h} 5.3 |
\section "I 连奏滑音 / 换把滑音"
:4 5.3{sl} 7.3 7.3{ss} 5.3 |
\section "J 滑入 / 滑出"
:4 5.3{sib} 7.3{sia} 7.3{sou} 5.3{sod} |
\section "K 半音 / 全音 / 推放 / 预推"
:4 7.3{b (0 2)} 7.3{b (0 4)} 7.3{b (0 4 0)} 7.3{b (4 0)} |
\section "L 揉弦 / 颤音 / 轮指"
:4 5.3{v} 5.3{vw} 5.3{tr (7 16)} 5.3{tp 2} |
\section "M 自然 / 人工 / 拨片 / 点触泛音"
:4 12.1{nh} 5.3{ah 12} 5.3{ph 12} 5.3{th 12} |
\section "N 掌根制音 / 死音 / 幽灵音 / 断奏"
:4 0.6{pm} x.6 5.3{g} 5.3{st} |
\section "O 持续音 / 双音"
:4 (0.1{lr} 0.6{lr}) (1.2{lr} 0.5{lr}) (0.3{lr} 0.4{lr}) (0.1{lr} 0.6{lr}) |
\section "P 点弦 / 击勾组合"
:4 12.1{tt h} 5.1{h} 8.1{h} 5.1{lht} |
\section "Q 倚音 / 指法 / 拨弦方向"
5.3.16{gr} :4 7.3{lf 4 rf 2 sd} 5.3{su} 7.3{ac} 5.3{hac} |
\section "R 打弦 / 弹拨 / 拍弦 / 敲板"
:4 0.6{s} 0.3{p} (){ds} 0.1{glpf} |
\section "S 摇把 / 渐强 / 渐弱 / 力度"
:4 0.1{tb (0 -4 0) dy p} 0.1{cre} 0.1{dec} 0.1{dy f} |
\section "T 反复起点"
\ro :4 0.1 1.1 3.1 0.1 |
\section "U 第一房"
\ae (1) \rc 2 :2 1.1 0.1 |
\section "V 第二房 / 速度变化"
\ae (2) \tempo 80 :2 3.1 0.1 |
\section "W 全音符 / 延长记号"
:1 (0.1 1.2 0.3 2.4 3.5){fermata medium} |
\section "X 全小节休止 / 终止线"
:1 r
`;
