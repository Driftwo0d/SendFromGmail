package eu.gpartner;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Draft;
import com.google.api.services.gmail.model.Message;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class SendFromGmail {

	private static final String APPLICATION_NAME = "SendFromGmail";
	private static final String CLIENT_ID = "46470081136-cpurg1fcb6rd3cs7crj4jrm658omkuak.apps.googleusercontent.com";
	private static final String CLIENT_SECRET = "0eJBAXHZrj3Sz9dmQe1AvpDp";
	private static final List<String> SCOPES = Arrays.asList(GmailScopes.GMAIL_COMPOSE);
	private static final File DATA_STORE_DIRECTORY = new java.io.File(System.getProperty("user.home"), ".store/"+APPLICATION_NAME);
	
	public static void main(String[] args) throws IOException {
		
		OptionParser parser = new OptionParser();
		
		parser.accepts("help").forHelp();
        parser.accepts("user").withRequiredArg().ofType(String.class).describedAs("USER_ACCOUNT").required();
        parser.accepts("from").withRequiredArg().ofType(String.class).describedAs("SENDER_EMAIL").defaultsTo("--user");
        parser.accepts("to").withRequiredArg().ofType(String.class).describedAs("RECIPIENTS_EMAIL");
        parser.accepts("subject").withRequiredArg().ofType(String.class).describedAs("EMAIL_SUBJECT");
        parser.accepts("file").withRequiredArg().ofType(String.class).describedAs("FILE_PATH");
        parser.accepts("html").withRequiredArg().ofType(String.class).describedAs("HTML_BODY");
		
        try{
        	
	        OptionSet options = parser.parse(args);
	        
	        if(options.has("help")){
	        	parser.printHelpOn(System.out);
	        	return;
	        }
	        
		    String userId = (String) options.valueOf("user");
			String emailFrom = options.has("from") ? (String) options.valueOf("from") : userId;
			String emailTo = options.has("to") ? (String) options.valueOf("to") : null;
			String emailSubject = options.has("subject") ? (String) options.valueOf("subject") : null;
			String emailHtmlBody = options.has("html") ? (String) options.valueOf("html") : null;
			String emailAttachmentFilePath = options.has("file") ? (String) options.valueOf("file") : null;
			
			try {
				Gmail gmail = getGmailService(userId);
				
				MimeMessage email = createMultipartEmail(emailFrom, emailTo, emailSubject, emailHtmlBody, emailAttachmentFilePath);
				Message message = createMessageFromEmail(email);
				
				//Gmail API call
				Draft draft = new Draft();
				draft.setMessage(message);
				draft = gmail.users().drafts().create(userId, draft).execute();
				
				String messageId = draft.getMessage().getId();
				URI redirectURI = new URI("https://mail.google.com/mail/u/0/#drafts?compose="+messageId);
				System.out.println("Draft URL: "+redirectURI.toString());
				
				Desktop.getDesktop().browse(redirectURI);
	
			} catch (IOException e) {
				e.printStackTrace();
			} catch (MessagingException e) {
				e.printStackTrace();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
        }catch(OptionException e){
        	System.out.println("Usage:");
        	parser.printHelpOn(System.out);
        }
	}


	private static Gmail getGmailService(String userId){

		Gmail gmail = null;
		
		try {
			
			JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
			NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIRECTORY);

			GoogleAuthorizationCodeFlow flow =
					new GoogleAuthorizationCodeFlow
					.Builder(httpTransport, jsonFactory, CLIENT_ID, CLIENT_SECRET, SCOPES)
					.setDataStoreFactory(dataStoreFactory)
					.build();
	
			Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(userId);
	
			gmail = new Gmail.Builder(httpTransport, jsonFactory, credential)
					.setApplicationName(APPLICATION_NAME)
					.build();	
		
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return gmail;
		
	}
	
	private static MimeMessage createMultipartEmail(String from, String to, String subject, String htmlBody, String filePath){
		
		MimeMessage email = null;

		try {

			//Crafting email
			Properties props = new Properties();
			Session session = Session.getDefaultInstance(props, null);
			email = new MimeMessage(session);

			if(from != null && from.length()>0){
				InternetAddress fromAddress = new InternetAddress(from);
				email.setFrom(fromAddress);
			}

			if(to != null && to.length()>0){
				InternetAddress toAddress = new InternetAddress(to);
				email.addRecipient(MimeMessage.RecipientType.TO, toAddress);
			}

			if(subject != null){
				email.setSubject(subject);
			}

			if(htmlBody == null){
				htmlBody = "";
			}

			Multipart multipart = new MimeMultipart();

			//HTML body
			MimeBodyPart htmlMimeBodyPart = new MimeBodyPart();
			htmlMimeBodyPart.setContent(htmlBody, "text/html");
			htmlMimeBodyPart.setHeader("Content-Type", "text/html; charset=\"UTF-8\"");
			multipart.addBodyPart(htmlMimeBodyPart);

			//Attachment
			if(filePath != null && filePath.length() > 0){
			    MimeBodyPart attachmentMimeBodyPart = new MimeBodyPart();
			    
			    File file = new File(filePath);
			    DataSource source = new FileDataSource(file);
			    
			    attachmentMimeBodyPart.setDataHandler(new DataHandler(source));
			    attachmentMimeBodyPart.setFileName(file.getName());
			    String contentType = Files.probeContentType(file.toPath());
			    attachmentMimeBodyPart.setHeader("Content-Type", contentType + "; name=\"" + file.getName() + "\"");
			    attachmentMimeBodyPart.setHeader("Content-Transfer-Encoding", "base64");
		
				multipart.addBodyPart(attachmentMimeBodyPart);
			}

			email.setContent(multipart);

		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return email;
	}

	private static Message createMessageFromEmail(MimeMessage email) throws MessagingException, IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		email.writeTo(bytes);
		String encodedEmail = Base64.encodeBase64URLSafeString(bytes.toByteArray());
		Message message = new Message();
		message.setRaw(encodedEmail);
		return message;
	}

}
