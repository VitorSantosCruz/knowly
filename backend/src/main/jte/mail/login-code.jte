@param String code
@template.mail.layout(
    preheader = "Your knowly login code is " + code,
    body = @`
<h1 style="margin:0 0 8px; font-size:18px; font-weight:600; color:#0f172a;">Your login code</h1>
<p style="margin:0 0 24px; font-size:14px; line-height:1.5; color:#475569;">Enter this code to finish signing in. It expires in 10 minutes and can only be used once.</p>
<div style="background-color:#eef2ff; border-radius:12px; padding:16px; text-align:center;">
<span style="font-size:28px; font-weight:700; letter-spacing:0.3em; color:#4338ca;">${code}</span>
</div>
    `
)
