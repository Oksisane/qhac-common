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

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import com.quickhac.common.TEAMSGradeParser;
import com.quickhac.common.data.ClassGrades;

public class Runner {
	public static void main(String args[]) throws IOException {
		final String user = "stac";
		final String password = "stacstac";
		final String query = "cn=" + user + "&%5Bpassword%5D=" + password;
		
		final String host = "my.austinisd.org";
		final String path = "/WebNetworkAuth/";
		
		final Socket socket = SSLSocketFactory.getDefault().createSocket(host, 443);
		final PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
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
		
		final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		final char[] buffer = new char[1024];
		int len = 0;
        while ((len = reader.read(buffer)) > 0) {
        	response += new String(buffer, 0, len);
        	if (response.endsWith("\r\n\r\n")) {
        		break;
        	}
        }
        
        for (String line: response.split("\n")) {
        	if (line.startsWith("Set-Cookie: CStoneSessionID=")) {
        		cstonecookie = line.substring(12);
        	}
        }
        
        if (cstonecookie == null) {
        	System.out.println("No cookie received!");
        	System.exit(1);
        }
        
        System.out.println(cstonecookie);
		
//		String html = readFile("test.html");
//		TEAMSGradeParser parser = new TEAMSGradeParser();
//		ClassGrades c = parser.parseClassGrades(html,"",0,0);
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
}