package com.quickhac.common;

import java.io.IOException;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.quickhac.common.data.StudentInfo;
import com.quickhac.common.districts.GradeSpeedDistrict;
import com.quickhac.common.err.InvalidGradeSpeedOutputException;
import com.quickhac.common.http.ASPNETPageState;
import com.quickhac.common.http.VerifiedHttpClientFactory;
import com.quickhac.common.http.XHR;

public class TEAMSGradeRetriever {
	
	final DefaultHttpClient client;
	final GradeSpeedDistrict district;
	
	/**
	 * Creates a new grade retriever for the specified district.
	 */
	public TEAMSGradeRetriever(final GradeSpeedDistrict d) {
		client = new VerifiedHttpClientFactory().getNewHttpClient();
		client.setRedirectStrategy(new XHR.RedirectStrategy());
		district = d;
	}
	
	/**
	 * Logs into a district grades website with the given credentials and
	 * specified callback. 
	 */
	public void login(final String user, final String pass, final LoginResponseHandler handler) {
		final XHR.ResponseHandler getDisambigChoices = new XHR.ResponseHandler() {

			@Override
			public void onSuccess(String response) {
				final Document doc = Jsoup.parse(response);
				if (!district.isValidOutput(doc)) {
					handler.onFailure(new InvalidGradeSpeedOutputException());
					return;
				}
				
				final ASPNETPageState state = ASPNETPageState.parse(doc);
				
				if (district.requiresDisambiguation(doc)) {
					
					// load the picker if necessary
					if (district.disambiguatePickerLoadsFromAjax()) {
						final XHR.ResponseHandler pickerLoaded = new XHR.ResponseHandler() {
							@Override
							public void onSuccess(String response) {
								final Document doc = Jsoup.parse(response);
								// don't check for valid GradeSpeed output
								final StudentInfo[] choices = district.getDisambiguationChoices(doc);
								handler.onRequiresDisambiguation(response, choices, state);
							}
							@Override
							public void onFailure(Exception e) {
								handler.onFailure(e);
							}
						};
						XHR.send(client, "GET", district.disambiguateURL(), null, pickerLoaded);
					} else {
						final StudentInfo[] choices = district.getDisambiguationChoices(doc);
						handler.onRequiresDisambiguation(response, choices, state);
					}
					
				} else handler.onDoesNotRequireDisambiguation(response);
			}

			@Override
			public void onFailure(Exception e) {
				handler.onFailure(e);
			}
			
		};
		
		final XHR.ResponseHandler doLogin = new XHR.ResponseHandler() {

			@Override
			public void onSuccess(String response) {
				final Document doc = Jsoup.parse(response);
				final ASPNETPageState state = ASPNETPageState.parse(doc);
				final HashMap<String, String> query = district.makeLoginQuery(user, pass, state);
				XHR.send(client, district.loginMethod(), district.loginURL(), query, getDisambigChoices);
			}

			@Override
			public void onFailure(Exception e) {
				handler.onFailure(e);
			}
			
		};
		
		XHR.send(client, "GET", district.loginURL(), null, doLogin);
	}
	
	public void disambiguate(final String studentId, final ASPNETPageState state, final XHR.ResponseHandler handler) {
		HashMap<String, String> query = district.makeDisambiguateQuery(studentId, state);
		
		XHR.send(client, district.disambiguateMethod(), district.disambiguateURL(), query, makeValidatedHandler(handler));
	}
	
	public void getAverages(final XHR.ResponseHandler handler) {
		XHR.send(client, district.gradesMethod(), district.gradesURL(), null, makeValidatedHandler(handler));
	}
	
	public void getCycle(final String urlHash, final Document gradesPage, final XHR.ResponseHandler handler) {
		ASPNETPageState state = null;
		
		// parse grades page if necessary
		if (district.cycleGradesRequiresAveragesLoaded())
			if (gradesPage == null)
				throw new IllegalArgumentException("District requires that averages be loaded before cycle grades can be loaded but no averages page was passed.");
			else
				state = ASPNETPageState.parse(gradesPage);
		
		final HashMap<String, String> query = district.makeCycleQuery(urlHash, state);
		XHR.send(client, district.cycleMethod(), district.cycleURL(), query, makeValidatedHandler(handler));
	}
	
	private XHR.ResponseHandler makeValidatedHandler(final XHR.ResponseHandler handler) {
		return new XHR.ResponseHandler() {

			@Override
			public void onSuccess(String response) {
				Document doc = Jsoup.parse(response);
				if (!district.isValidOutput(doc)) {
					handler.onFailure(new InvalidGradeSpeedOutputException());
					return;
				}
				
				handler.onSuccess(response);
			}

			@Override
			public void onFailure(Exception e) {
				handler.onFailure(e);
			}
			
		};
	}
	
	public static abstract class LoginResponseHandler {
		public abstract void onRequiresDisambiguation(String response, StudentInfo[] students, ASPNETPageState state);
		public abstract void onDoesNotRequireDisambiguation(String response);
		public abstract void onFailure(Exception e);
	}
	public static String getGradesPage(String AISDuser, String AISDpass) {
		try {
			WebClient webClient = getTEAMSConnection(AISDuser, AISDpass);
			// Load Teams
			final HtmlPage page3 = webClient
					.getPage("https://my-teams.austinisd.org/selfserve/PSSViewReportCardsAction.do");
			System.out.println(page3.asText());
			return page3.asXml();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	private static WebClient getTEAMSConnection(String AISDuser, String AISDpass) {
		try {
			// Extract jSessionId from startURL for Cookies
			final WebClient webClient = new WebClient(BrowserVersion.CHROME);
			WebClientOptions config = webClient.getOptions();
			config.setThrowExceptionOnScriptError(false);
			config.setCssEnabled(false);
			cookieSet(webClient, AISDuser, AISDpass);

			// Get Teams Login Page
			HtmlPage page = webClient
					.getPage("https://my-teams.austinisd.org/selfserve/EntryPointSignOnAction.do?parent=false");

			// Get and fill out form
			HtmlForm form = page.getFormByName("SmartForm");
			HtmlTextInput username = form.getInputByName("userLoginId");
			HtmlPasswordInput password = form.getInputByName("userPassword");
			username.setValueAttribute(AISDuser);
			password.setValueAttribute(AISDpass);

			// Submit form
			String javaScriptCode = "var form = (document.forms['SmartForm']); form.action = \"https://my-teams.austinisd.org/selfserve/SignOnLoginAction.do?parent=false\"; form.submit();";
			page.executeJavaScript(javaScriptCode);
			return webClient;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static void cookieSet(final WebClient webClient,
			final String AISDuser, final String AISDpass) {
		try {
			HtmlPage page = webClient.getPage("https://my.austinisd.org/");
			HtmlForm form = page.getFormByName("loginForm");
			HtmlTextInput username = form.getInputByName("cn");
			HtmlPasswordInput password = form.getInputByName("[password]");
			username.setValueAttribute(AISDuser);
			password.setValueAttribute(AISDpass);
			String javaScriptCode = "document.forms['loginForm'].submit();";
			page.executeJavaScript(javaScriptCode);

			boolean failure = true;
			for (final Cookie cookie : webClient.getCookieManager()
					.getCookies()) {
				if (cookie.getName().equalsIgnoreCase("CSTONESESSIONID")) {
					failure = false;
				}
			}
			if (failure) {
				System.out.println("Incorrect username/password.");
				System.exit(1);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}