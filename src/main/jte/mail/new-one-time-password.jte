@param String password
@template.mail.layout(
    preheader = "Your new knowly one-time password",
    body = @`
<h1 style="margin:0 0 8px; font-size:18px; font-weight:600; color:#0f172a;">Your new one-time password</h1>
<p style="margin:0 0 24px; font-size:14px; line-height:1.5; color:#475569;">Use it the next time you log in with a password. It's valid for 15 days or until used, whichever comes first.</p>
<div style="background-color:#eef2ff; border-radius:12px; padding:16px; text-align:center;">
<span style="font-size:20px; font-weight:700; letter-spacing:0.15em; color:#4338ca; word-break:break-all;">${password}</span>
</div>
    `
)
