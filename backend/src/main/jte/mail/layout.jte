@import gg.jte.Content

@param String preheader
@param Content body
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>knowly</title>
</head>
<body style="margin:0; padding:0; background-color:#f1f5f9; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
<span style="display:none; font-size:1px; color:#f1f5f9; line-height:1px; max-height:0; max-width:0; opacity:0; overflow:hidden;">${preheader}</span>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f1f5f9;">
<tr>
<td align="center" style="padding:32px 16px;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;">
<tr>
<td style="padding-bottom:24px; text-align:center;">
<span style="font-size:20px; font-weight:700; color:#0f172a; letter-spacing:-0.02em;">knowly</span>
</td>
</tr>
<tr>
<td style="background-color:#ffffff; border-radius:16px; padding:32px; border:1px solid #e2e8f0;">
${body}
</td>
</tr>
<tr>
<td style="padding-top:24px; text-align:center;">
<p style="margin:0; font-size:12px; color:#94a3b8;">If you didn't request this, you can safely ignore this email.</p>
</td>
</tr>
</table>
</td>
</tr>
</table>
</body>
</html>
