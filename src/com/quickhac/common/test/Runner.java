package com.quickhac.common.test;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import ch.boye.httpclientandroidlib.Header;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;

import com.quickhac.common.TEAMSGradeParser;
import com.quickhac.common.data.ClassGrades;
import com.quickhac.common.http.ASPNETPageState;
import com.quickhac.common.http.VerifiedHttpClientFactory;
import com.quickhac.common.http.XHR;

public class Runner {


	public static void main(String args[]) throws IOException {
		// String html = readFile("test.html");
		// TEAMSGradeParser parser = new TEAMSGradeParser();
		// ClassGrades c = parser.parseClassGrades(html,"",0,0);
		// Prompt user for username and password
		final JLabel usernameLabel = new JLabel("Username:");
		final JTextField usernameField = new JTextField();
		final JLabel passwordLabel = new JLabel("Password:");
		final JPasswordField passwordField = new JPasswordField();
		JOptionPane.showConfirmDialog(null, new Object[] { usernameLabel,
				usernameField, passwordLabel, passwordField }, "Login",
				JOptionPane.OK_CANCEL_OPTION);

		final String AISDuser = usernameField.getText();
		final String AISDpass = new String(passwordField.getPassword());
		//String cstonecookie = getAustinisdCookie(AISDuser, AISDpass);
		//String teamscookie = getTEAMSCookie(AISDuser, AISDpass, cstonecookie);
		DefaultHttpClient client = new VerifiedHttpClientFactory().getNewHttpClient();
		final XHR.ResponseHandler getGradesPage = new XHR.ResponseHandler() {

			@Override
			public void onSuccess(String response) {
				//System.out.println(response);
			}

			@Override
			public void onFailure(Exception e) {
			}

		};
		//String finalcookie = teamscookie + ';' + cstonecookie;
		//System.out.println(finalcookie);	
		String gradeBookKey = "gradeBookKey=sectionIndex%253D2%252CgradeTypeIndex%253D1st%2BSix%2BWeeks%252CcourseIndex%253D4435.P100.Y%252CcalendarIndex%253D1%252CgradeIndex%253D95%252CteacherIndex%253DStormberg%255E%2BJohn%252CdayCodeIndex%253DA%2B-%2B04%252ClocIndex%253D018";
		String html = getTEAMSPage("/selfserve/PSSViewReportCardsAction.do", "", "");
		System.out.println(html);
	}

	static String readFile(String fileName) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append("\n");
				line = br.readLine();
			}
			return sb.toString();
		} finally {
			br.close();
		}
	}

	public static String getAustinisdCookie(final String AISDuser,
			final String AISDpass) throws UnknownHostException, IOException {
		final String user = AISDuser;
		final String password = AISDpass;
		final String query = "cn=" + user + "&%5Bpassword%5D=" + password;

		final String host = "my.austinisd.org";
		final String path = "/WebNetworkAuth/";

		final Socket socket = SSLSocketFactory.getDefault().createSocket(host,
				443);
		final PrintWriter writer = new PrintWriter(new OutputStreamWriter(
				socket.getOutputStream()));
		writer.println("POST " + path + " HTTP/1.1");
		writer.println("Host: " + host);
		writer.println("Accept: */*");
		writer.println("User-Agent: QHAC");
		writer.println("Content-Type: application/x-www-form-urlencoded");
		writer.println("Content-Length: " + query.length());
		writer.println();
		writer.println(query);
		writer.println();
		writer.flush();

		String cstonecookie = null;

		String response = "";

		final BufferedReader reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
		final char[] buffer = new char[1024];
		int len = 0;
		while ((len = reader.read(buffer)) > 0) {
			response += new String(buffer, 0, len);
			if (response.endsWith("\r\n\r\n")) {
				break;
			}
		}

		for (String line : response.split("\n")) {
			if (line.startsWith("Set-Cookie: CStoneSessionID=")) {
				cstonecookie = line.substring(12);
			}
		}

		if (cstonecookie == null) {
			System.out.println("No cookie received!");
			System.exit(1);
		}

		System.out.println(cstonecookie);
		// Split on first semicolon but keep first semicolon
		return cstonecookie.split(";")[0];
	}

	public static String getTEAMSCookie(final String AISDuser,
			final String AISDpass, final String CStoneCookie)
			throws UnknownHostException, IOException {
		DefaultHttpClient client = new VerifiedHttpClientFactory().getNewHttpClient();
		final XHR.ResponseHandler getGradesPage = new XHR.ResponseHandler() {

			@Override
			public void onSuccess(String response) {
				//System.out.println(response);
			}

			@Override
			public void onFailure(Exception e) {
			}

		};
		HashMap<String, String> par = new HashMap<String, String>();
		par.put("userLoginId", AISDuser);
		par.put("userPassword", AISDpass);
		Header[] cookiesToSet = XHR.getCookie(client,"https://my-teams.austinisd.org/selfserve/SignOnLoginAction.do",
				par, CStoneCookie);
		return cookiesToSet[0].getValue().split(";")[0];
	}
	public static String getTEAMSPage(final String path,
			final String gradeBookKey, String cookie) throws UnknownHostException, IOException {
		final String host = "my-teams.austinisd.org";
		final Socket socket = SSLSocketFactory.getDefault().createSocket(host,
				443);
		final PrintWriter writer = new PrintWriter(new OutputStreamWriter(
				socket.getOutputStream()));
		writer.println("POST " + path + " HTTP/1.1");
		writer.println("Host: " + host);
		writer.println("Cookie: " + cookie);
		writer.println("Cache-Control: no-cache");
		writer.println("Content-Type: application/x-www-form-urlencoded");
		writer.println();
		writer.println(gradeBookKey);
		writer.println();
		writer.flush();

		String response = "";

		final BufferedReader reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
		final char[] buffer = new char[1024];
		int len = 0;
		while ((len = reader.read(buffer)) > 0) {
			response += new String(buffer, 0, len);
			if (response.endsWith("\r\n\r\n")) {
				break;
			}
		}

		return response;
	}
}