package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Resend.com HTTP API implementation of EmailNotificationGateway.
 *
 * Activated when app.email.provider=resend.
 * Requires:
 *   - RESEND_API_KEY environment variable (or app.email.resend-api-key in application.yml)
 *   - app.email.from-address (e.g. "Stores System <noreply@yourdomain.com>")
 *
 * No Maven dependency needed — uses Java 11+ built-in HttpClient.
 * Falls back gracefully (logs error, never breaks the primary transaction).
 *
 * Resend docs: https://resend.com/docs/api-reference/emails/send-email
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
public class ResendEmailNotificationGateway implements EmailNotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailNotificationGateway.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String fromAddress;
    private final HttpClient httpClient;

    public ResendEmailNotificationGateway(
            @Value("${app.email.resend-api-key:}") String apiKey,
            @Value("${app.email.from-address:noreply@example.com}") String fromAddress) {
        this.apiKey      = apiKey;
        this.fromAddress = fromAddress;
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void send(AppUser user, String subject, String message) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Resend: skipping email — recipient has no email address");
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend: RESEND_API_KEY not set — email not sent to {}", user.getEmail());
            return;
        }

        // Build an HTML body for a nicer look in email clients
        String htmlBody = buildHtmlEmail(user.getFullName(), subject, message);

        // JSON payload — Resend accepts "html" and "text" fields
        String jsonPayload = "{"
                + "\"from\": " + jsonString(fromAddress) + ","
                + "\"to\": [" + jsonString(user.getEmail()) + "],"
                + "\"subject\": " + jsonString(subject) + ","
                + "\"html\": " + jsonString(htmlBody) + ","
                + "\"text\": " + jsonString(message)
                + "}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Resend email sent to {} — subject: {}", user.getEmail(), subject);
            } else {
                log.error("Resend API error {} for {}: {}", response.statusCode(), user.getEmail(), response.body());
            }
        } catch (Exception e) {
            // Email failure must never crash the primary business transaction
            log.error("Resend send failed to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Minimal JSON string escaping for the inline payload. */
    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    /** Simple branded HTML email template using provided HTML structure. */
    private String buildHtmlEmail(String recipientName, String subject, String body) {
        String safeBody = body
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        String safeName = recipientName != null ? recipientName : "User";

        String template = """
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html dir="ltr" xmlns="http://www.w3.org/1999/xhtml" xmlns:o="urn:schemas-microsoft-com:office:office" lang="en">
 <head>
  <meta charset="UTF-8">
  <meta content="width=device-width, initial-scale=1" name="viewport">
  <meta name="x-apple-disable-message-reformatting">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <meta content="telephone=no" name="format-detection">
  <title>{{SUBJECT}}</title><!--[if (mso 16)]>
    <style type="text/css">
        a {
            text-decoration: none;
        }
    </style>
    <![endif]--><!--[if gte mso 9]>
    <style>sup {
        font-size: 100% !important;
    }</style><![endif]--><!--[if gte mso 9]>
    <xml>
        <o:OfficeDocumentSettings>
            <o:AllowPNG></o:AllowPNG>
            <o:PixelsPerInch>96</o:PixelsPerInch>
        </o:OfficeDocumentSettings>
    </xml>
    <![endif]-->
  <style type="text/css">
#outlook a {
    padding:0;
}
.es-button {
    mso-style-priority:100!important;
    text-decoration:none!important;
}
a[x-apple-data-detectors] {
    color:inherit!important;
    text-decoration:none!important;
    font-size:inherit!important;
    font-family:inherit!important;
    font-weight:inherit!important;
    line-height:inherit!important;
}
.es-desk-hidden {
    display:none;
    float:left;
    overflow:hidden;
    width:0;
    max-height:0;
    line-height:0;
    mso-hide:all;
}
@media only screen and (max-width:600px) {p, ul li, ol li, a { line-height:150%!important } h1, h2, h3, h1 a, h2 a, h3 a { line-height:120%!important } h1 { font-size:40px!important; text-align:left; margin-bottom:0px } h2 { font-size:28px!important; text-align:left; margin-bottom:0px } h3 { font-size:20px!important; text-align:left; margin-bottom:0px } .es-header-body h1 a, .es-content-body h1 a, .es-footer-body h1 a { font-size:40px!important; text-align:left } .es-header-body h2 a, .es-content-body h2 a, .es-footer-body h2 a { font-size:28px!important; text-align:left } .es-header-body h3 a, .es-content-body h3 a, .es-footer-body h3 a { font-size:20px!important; text-align:left } .es-menu td a { font-size:14px!important } .es-header-body p, .es-header-body ul li, .es-header-body ol li, .es-header-body a { font-size:14px!important } .es-content-body p, .es-content-body ul li, .es-content-body ol li, .es-content-body a { font-size:14px!important } .es-footer-body p, .es-footer-body ul li, .es-footer-body ol li, .es-footer-body a { font-size:14px!important } .es-infoblock p, .es-infoblock ul li, .es-infoblock ol li, .es-infoblock a { font-size:12px!important } *[class="gmail-fix"] { display:none!important } .es-m-txt-c, .es-m-txt-c h1, .es-m-txt-c h2, .es-m-txt-c h3 { text-align:center!important } .es-m-txt-r, .es-m-txt-r h1, .es-m-txt-r h2, .es-m-txt-r h3 { text-align:right!important } .es-m-txt-l, .es-m-txt-l h1, .es-m-txt-l h2, .es-m-txt-l h3 { text-align:left!important } .es-m-txt-r img, .es-m-txt-c img, .es-m-txt-l img { display:inline!important } .es-button-border { display:inline-block!important } a.es-button, button.es-button { font-size:18px!important; display:inline-block!important } .es-adaptive table, .es-left, .es-right { width:100%!important } .es-content table, .es-header table, .es-footer table, .es-content, .es-footer, .es-header { width:100%!important; max-width:600px!important } .es-adapt-td { display:block!important; width:100%!important } .adapt-img { width:100%!important; height:auto!important } .es-m-p0 { padding:0!important } .es-m-p0r { padding-right:0!important } .es-m-p0l { padding-left:0!important } .es-m-p0t { padding-top:0!important } .es-m-p0b { padding-bottom:0!important } .es-m-p20b { padding-bottom:20px!important } .es-mobile-hidden, .es-hidden { display:none!important } tr.es-desk-hidden, td.es-desk-hidden, table.es-desk-hidden { width:auto!important; overflow:visible!important; float:none!important; max-height:inherit!important; line-height:inherit!important } tr.es-desk-hidden { display:table-row!important } table.es-desk-hidden { display:table!important } td.es-desk-menu-hidden { display:table-cell!important } .es-menu td { width:1%!important } table.es-table-not-adapt, .esd-block-html table { width:auto!important } table.es-social { display:inline-block!important } table.es-social td { display:inline-block!important } .es-desk-hidden { display:table-row!important; width:auto!important; overflow:visible!important; max-height:inherit!important } .es-m-p5 { padding:5px!important } .es-m-p5t { padding-top:5px!important } .es-m-p5b { padding-bottom:5px!important } .es-m-p5r { padding-right:5px!important } .es-m-p5l { padding-left:5px!important } .es-m-p10 { padding:10px!important } .es-m-p10t { padding-top:10px!important } .es-m-p10b { padding-bottom:10px!important } .es-m-p10r { padding-right:10px!important } .es-m-p10l { padding-left:10px!important } .es-m-p15 { padding:15px!important } .es-m-p15t { padding-top:15px!important } .es-m-p15b { padding-bottom:15px!important } .es-m-p15r { padding-right:15px!important } .es-m-p15l { padding-left:15px!important } .es-m-p20 { padding:20px!important } .es-m-p20t { padding-top:20px!important } .es-m-p20r { padding-right:20px!important } .es-m-p20l { padding-left:20px!important } .es-m-p25 { padding:25px!important } .es-m-p25t { padding-top:25px!important } .es-m-p25b { padding-bottom:25px!important } .es-m-p25r { padding-right:25px!important } .es-m-p25l { padding-left:25px!important } .es-m-p30 { padding:30px!important } .es-m-p30t { padding-top:30px!important } .es-m-p30b { padding-bottom:30px!important } .es-m-p30r { padding-right:30px!important } .es-m-p30l { padding-left:30px!important } .es-m-p35 { padding:35px!important } .es-m-p35t { padding-top:35px!important } .es-m-p35b { padding-bottom:35px!important } .es-m-p35r { padding-right:35px!important } .es-m-p35l { padding-left:35px!important } .es-m-p40 { padding:40px!important } .es-m-p40t { padding-top:40px!important } .es-m-p40b { padding-bottom:40px!important } .es-m-p40r { padding-right:40px!important } .es-m-p40l { padding-left:40px!important } p, ul li, ol li { margin-bottom:11px!important } .es-header-body p, .es-header-body ul li, .es-header-body ol li { margin-bottom:11px!important } .es-footer-body p, .es-footer-body ul li, .es-footer-body ol li { margin-bottom:11px!important } .es-infoblock p, .es-infoblock ul li, .es-infoblock ol li { margin-bottom:9px!important } }
@media screen and (max-width:384px) {.mail-message-content { width:414px!important } }
</style>
 </head>
 <body style="width:100%;font-family:Lexend, Arial, sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;padding:0;Margin:0">
  <div dir="ltr" class="es-wrapper-color" lang="en" style="background-color:#E6F9FE"><!--[if gte mso 9]>
    <v:background xmlns:v="urn:schemas-microsoft-com:vml" fill="t">
        <v:fill type="tile" color="#f6f6f6"></v:fill>
    </v:background>
    <![endif]-->
   <table class="es-wrapper" width="100%" cellspacing="0" cellpadding="0" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;padding:0;Margin:0;width:100%;height:100%;background-repeat:repeat;background-position:center top;background-color:#E6F9FE">
    <tbody>
     <tr>
      <td valign="top" style="padding:0;Margin:0">
       <table cellpadding="0" cellspacing="0" class="es-header" align="center" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%;background-color:transparent;background-repeat:repeat;background-position:center top">
        <tbody>
         <tr>
          <td align="center" bgcolor="transparent" style="padding:0;Margin:0">
           <table align="center" cellpadding="0" cellspacing="0" bgcolor="#ffffff" class="es-header-body" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:transparent;width:600px">
            <tbody>
             <tr>
              <td align="left" style="Margin:0;padding:20px 30px 15px">
               <table cellspacing="0" width="100%" cellpadding="0" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px">
                <tbody>
                 <tr>
                  <td align="left" style="padding:0;Margin:0;width:540px">
                   <table cellpadding="0" cellspacing="0" width="100%" role="presentation" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px">
                    <tbody>
                     <tr>
                      <td align="center" style="padding:0;Margin:0;font-size:0px"><img alt="" height="83" src="https://fagrvyi.stripocdn.email/content/guids/CABINET_a94a42603a7bd623d9fb8086a141871d5e70465775569b92eeef25cc046e88df/images/logoplaceholder.png" class="companyLogo" style="display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic"></td>
                     </tr>
                    </tbody>
                   </table></td>
                 </tr>
                </tbody>
               </table></td>
             </tr>
            </tbody>
           </table></td>
         </tr>
        </tbody>
       </table>
       
       <table cellpadding="0" cellspacing="0" class="es-content" align="center" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%">
        <tbody>
         <tr>
          <td align="center" style="padding:0;Margin:0">
           <table bgcolor="#ffffff" align="center" cellpadding="0" cellspacing="0" class="es-content-body" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:#FFFFFF;width:600px">
            <tbody>
             <tr>
              <td align="left" style="Margin:0;padding:10px 30px 30px">
               <table cellpadding="0" cellspacing="0" width="100%" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px">
                <tbody>
                 <tr>
                  <td align="center" valign="top" style="padding:0;Margin:0;width:540px">
                   <table cellpadding="0" cellspacing="0" width="100%" role="presentation" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px">
                    <tbody>
                     <tr>
                      <td align="center" style="padding:10px 0 20px;Margin:0"><h2 class="p_title es-m-txt-c" style="Margin:0;line-height:34px;mso-line-height-rule:exactly;font-family:Lexend, Arial, sans-serif;font-size:28px;font-style:normal;font-weight:normal;color:#0D4259;margin-bottom:0px">{{SUBJECT}}</h2></td>
                     </tr>
                     <tr>
                      <td align="left" class="generalTextHtml" style="padding:0;Margin:0">
                        <p style="Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:Lexend, Arial, sans-serif;line-height:21px;margin-bottom:10px;color:#0D4259;font-size:14px">Dear {{NAME}},</p>
                        <p style="Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:Lexend, Arial, sans-serif;line-height:21px;margin-bottom:10px;color:#0D4259;font-size:14px">{{BODY}}</p>
                      </td>
                     </tr>
                    </tbody>
                   </table></td>
                 </tr>
                </tbody>
               </table></td>
             </tr>
            </tbody>
           </table></td>
         </tr>
        </tbody>
       </table>
       
       <table cellpadding="0" cellspacing="0" class="es-header" align="center" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%;background-color:transparent;background-repeat:repeat;background-position:center top">
        <tbody>
         <tr>
          <td align="center" bgcolor="transparent" style="padding:0;Margin:0">
           <table align="center" cellpadding="0" cellspacing="0" bgcolor="#ffffff" class="es-footer-body" role="none" style="mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:#BCF0FF;width:600px">
            <tbody>
             <tr>
              <td align="center" style="padding:20px 30px;Margin:0">
                <p style="margin:0;color:#0D4259;font-size:13px;font-family:Lexend, Arial, sans-serif;">New Sahara Ventures (Pvt) Ltd &middot; 43 Coull Drive, Mount Pleasant, Harare &middot; Zimbabwe</p>
              </td>
             </tr>
            </tbody>
           </table></td>
         </tr>
        </tbody>
       </table></td>
     </tr>
    </tbody>
   </table>
  </div>
 </body>
</html>
""";

        return template
                .replace("{{SUBJECT}}", subject)
                .replace("{{NAME}}", safeName)
                .replace("{{BODY}}", safeBody);
    }

}
