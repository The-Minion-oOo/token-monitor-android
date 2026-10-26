# Languages

**v0.79.0 r2** adds twelve interface languages. Earlier APKs have an English
interface. The language packs are bundled; selecting a language never contacts
a translation service.

Choose **Settings → App language**, or use Android's per-app language setting
on Android 13 and newer. **Follow phone language** is the default. The in-app
picker also works on Android 8–12. Switching language keeps pairing and saved
view/module keys intact and redraws placed widgets from their saved snapshot.

| Language | Name in the picker |
| --- | --- |
| English | English |
| Simplified Chinese | 简体中文 |
| Traditional Chinese | 繁體中文 |
| Korean | 한국어 |
| Japanese | 日本語 |
| Brazilian Portuguese | Português (Brasil) |
| Spanish | Español |
| Hindi | हिन्दी |
| Gujarati | ગુજરાતી |
| Bengali | বাংলা |
| Tamil | தமிழ் |
| Telugu | తెలుగు |

App controls, explanations and widget labels use these resources. Tool/provider
names, Hub-supplied window titles, projects, session titles, notes and remote
error text remain as reported. Compact units such as K/M/B and d/h/m stay
consistent across languages to fit existing chart and widget slots. Diagnostic
report field names remain stable English keys for troubleshooting.

I check resource coverage, format placeholders, emulator switching, font fallback
and captured dashboard/widget renders. These checks do not replace native-speaker
wording review. Corrections are welcome: include the language, current wording,
suggested wording and the screen, using synthetic or redacted examples.

## 中文快速开始

Token Monitor Android 是桌面 Token Monitor 的只读移动看板。查看 Token 用量、
费用估算、账户额度、模型、项目、会话与历史趋势，无需在手机上重新采集数据。

从本仓库的 [Releases](https://github.com/The-Minion-oOo/token-monitor-android/releases/latest)
下载 APK。在桌面应用中启用 Hub，推荐通过 Tailscale 连接。首次配对输入 Hub 地址
和共享密钥。手机不会控制桌面，也不会读取对话正文。r2 可在应用语言设置中选择简体中文或繁體中文。

## Quick introductions

**Español:** Consulta el uso de tokens, los costes estimados, las cuotas y las
tendencias de tu escritorio desde Android. Los datos se recopilan en el
escritorio; el teléfono muestra un panel de solo lectura.

**हिन्दी:** अपने डेस्कटॉप के टोकन उपयोग, अनुमानित लागत, कोटा और रुझान Android पर
देखें। डेटा डेस्कटॉप पर एकत्र होता है; फ़ोन केवल जानकारी दिखाता है।

**ગુજરાતી:** તમારા ડેસ્કટોપના ટોકન વપરાશ, અંદાજિત ખર્ચ, ક્વોટા અને વલણો Android પર
જુઓ. ડેટા ડેસ્કટોપ પર એકત્ર થાય છે; ફોન માત્ર માહિતી દર્શાવે છે.

**বাংলা:** Android-এ আপনার ডেস্কটপের টোকেন ব্যবহার, আনুমানিক খরচ, কোটা ও প্রবণতা
দেখুন। তথ্য ডেস্কটপে সংগ্রহ করা হয়; ফোন শুধু সেই তথ্য দেখায়।

**தமிழ்:** உங்கள் கணினியின் டோக்கன் பயன்பாடு, மதிப்பிடப்பட்ட செலவு, ஒதுக்கீடு மற்றும்
போக்குகளை Android-இல் பாருங்கள். தரவு கணினியில் சேகரிக்கப்படுகிறது; தொலைபேசி அதை மட்டும் காட்டுகிறது.

**తెలుగు:** మీ డెస్క్‌టాప్ టోకెన్ వినియోగం, అంచనా ఖర్చు, కోటా మరియు ధోరణులను
Androidలో చూడండి. డేటా డెస్క్‌టాప్‌లో సేకరించబడుతుంది; ఫోన్ ఆ సమాచారాన్ని మాత్రమే చూపిస్తుంది.

**日本語:** デスクトップのトークン使用量、推定コスト、利用枠、傾向を Android で確認できます。
データ収集はデスクトップで行い、スマートフォンは読み取り専用で表示します。

**한국어:** 데스크톱의 토큰 사용량, 예상 비용, 할당량과 추세를 Android에서 확인하세요.
데이터는 데스크톱에서 수집하며 휴대전화는 읽기 전용으로 표시합니다.

**Português (Brasil):** Acompanhe tokens, custos estimados, cotas e tendências
do desktop no Android. A coleta fica no desktop; o celular exibe um painel
somente de leitura.

[Install and update](INSTALL.md) · [Pairing](PAIRING.md) · [Desktop coverage](DESKTOP_PARITY.md)
