
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.net.ssl.SSLSocketFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.quickhac.common.TEAMSGradeParser;
import com.quickhac.common.data.ClassGrades;
import com.quickhac.common.data.Course;

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
		TEAMSGradeParser p = new TEAMSGradeParser();
		final String AISDuser =  usernameField.getText();
		final String AISDpass =  new String(passwordField.getPassword());
		//Get cookies
		final String cstonecookie = getAustinisdCookie(AISDuser, AISDpass);
		final String teamscookie = getTEAMSCookie(cstonecookie);
		
		//Generate final cookie
		String finalcookie = teamscookie + ';' + cstonecookie;
		System.out.println(finalcookie);	
		
		//POST to login to TEAMS
		postTEAMSLogin(AISDuser,AISDpass,finalcookie);
		
		//Get "Report Card"
		String averageHtml = getTEAMSPage("/selfserve/PSSViewReportCardsAction.do", "", finalcookie);
		Course[] studentCourses = p.parseAverages(averageHtml);
		System.out.println(averageHtml);
		
		//Parse averageHtml
		for (Course c: studentCourses){
			System.out.println("Teacher: " + c.teacherName + " Class: " + c.title);
		}
		
		/*Logic to get ClassGrades. TEAMS looks for a post request with the "A" tag id of a specific grade selected, 
		 * so we iterate through all the a tags we got above and send/store the parsed result one by one*/
		ArrayList<ClassGrades> classGrades = new ArrayList<ClassGrades>();
		Elements avalues = Jsoup.parse(averageHtml).getElementById("finalTablebottomRight1").getElementsByTag("a");
		for (Element e: avalues){
			if (isNumeric(e.text())){
				String gradeBookKey = "selectedIndexId=-1&smartFormName=SmartForm&gradeBookKey=" + URLEncoder.encode(e.id(), "UTF-8");
				classGrades.add( p.parseClassGrades(getTEAMSPage("/selfserve/PSSViewGradeBookEntriesAction.do", gradeBookKey, finalcookie), "", 0, 0));
			}
		}
		System.out.println("Success");
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
		final String query = "cn=" + AISDuser + "&%5Bpassword%5D=" + AISDpass;
		
		final String response = postPageHTTPS("my.austinisd.org", "/WebNetworkAuth/", new String[]{
				"User-Agent: QHAC",
				"Accept: */*"
		}, query);
		
		String cstonecookie = null;

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
		// Split on first semicolon
		return cstonecookie.split(";")[0];
	}


	public static String getTEAMSCookie(final String CStoneCookie)
			throws UnknownHostException, IOException {
		final String query = "";
		final String response = postPageHTTPS("my-teams.austinisd.org", "/selfserve/EntryPointSignOnAction.do?parent=false", new String[]{
				"Cookie: " + CStoneCookie,
				"Accept: */*",
				"User-Agent: QHAC"
		}, query);
		String jcookie = null;

		for (String line : response.split("\n")) {
			if (line.startsWith("Set-Cookie: JSESSIONID=")) {
				jcookie = line.substring(12);
			}
		}

		if (jcookie == null) {
			System.out.println("No cookie received!");
			System.exit(1);
		}
		System.out.println(jcookie);
		// Split on first semicolon
		return jcookie.split(";")[0];
	}

	public static void postTEAMSLogin(final String AISDuser,
			final String AISDpass, final String CStoneCookie)
			throws UnknownHostException, IOException {
		final String query = "userLoginId=" + AISDuser + "&userPassword=" + AISDpass;
		final String response = postPageHTTPS("my-teams.austinisd.org", "/selfserve/SignOnLoginAction.do", new String[]{
				"Cookie: " + CStoneCookie,
				"Accept: */*",
				"User-Agent: QHAC"
		}, query);
	}
	public static String getTEAMSPage(final String path,
			final String gradeBookKey, String cookie) throws UnknownHostException, IOException {
		return postPageHTTPS("my-teams.austinisd.org", path, new String[]{
				"Cookie: " + cookie,
				"Referer: https://my-teams.austinisd.org/selfserve/EntryPointSignOnAction.do?parent=false"
		}, gradeBookKey);
	}
	
	public static String postPageHTTPS(final String host, final String path, final String[] headers, final String postData) throws UnknownHostException, IOException {
		final Socket socket = SSLSocketFactory.getDefault().createSocket(host,
				443);
		final PrintWriter writer = new PrintWriter(new OutputStreamWriter(
				socket.getOutputStream()));
		writer.println("POST " + path + " HTTP/1.1");
		writer.println("Host: " + host);
		for (String header : headers) {
			writer.println(header);
		}
		writer.println("Content-Length: " + postData.length());
		writer.println("Content-Type: application/x-www-form-urlencoded");
		writer.println();
		writer.println(postData);
		writer.println();
		writer.flush();

		StringBuilder response = new StringBuilder();

		final BufferedReader reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream()));
		final char[] buffer = new char[1024];
		int len = 0;
		while ((len = reader.read(buffer)) > 0) {
			response.append(buffer, 0, len);
			if (response.length() >= 4 && response.substring(response.length() - 4).equals("\r\n\r\n")) {
				break;
			}
		}

		return response.toString();
		
	}
	public static boolean isNumeric(String str)  
	{  
	  try  
	  {  
	    double d = Double.parseDouble(str);  
	  }  
	  catch(NumberFormatException nfe)  
	  {  
	    return false;  
	  }  
	  return true;  
	}
}
//DefaultHttpClient client = new VerifiedHttpClientFactory().getNewHttpClient();
//final XHR.ResponseHandler getGradesPage = new XHR.ResponseHandler() {
//
//	@Override
//	public void onSuccess(String response) {
//		//System.out.println(response);
//	}
//
//	@Override
//	public void onFailure(Exception e) {
//	}
//
//};
//HashMap<String, String> par = new HashMap<String, String>();
//par.put("userLoginId", AISDuser);
//par.put("userPassword", AISDpass);
//Header[] cookiesToSet = XHR.getCookie(client,"https://my-teams.austinisd.org/selfserve/SignOnLoginAction.do",
//		par, CStoneCookie);
//return cookiesToSet[0].getValue().split(";")[0];