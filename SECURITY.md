# Reporting security concerns

Supported compatibility baseline: desktop Token Monitor v0.81.0. Fixes are prepared
for the newest Android revision; older APKs are not separately maintained.

Do not post Hub secrets, private addresses, account details, raw Hub responses or
working exploits in public issues. Private vulnerability reporting is enabled on
this repository: use **Security → Report a vulnerability**.

I can't promise a response time, but I read every report.

The app's data, transport, storage and permission boundaries are documented in
[Security and privacy](docs/SECURITY.md).

Tailscale remains recommended. Internet Hub access is a separate opt-in, off by
default, limited to public DNS names over system-trusted HTTPS. Enabling it does
not expose a computer or deploy a Hub.
