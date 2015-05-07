package com.dds;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.JiraRestClientFactory;
import com.atlassian.jira.rest.client.RestClientException;
import com.atlassian.jira.rest.client.domain.Comment;
import com.atlassian.jira.rest.client.domain.Issue;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("myresource")
public class MyResource {

	/**
	 * Method handling HTTP GET requests. The returned object will be sent to
	 * the client as "text/plain" media type.
	 *
	 * @return String that will be returned as a text/plain response.
	 * @throws URISyntaxException
	 * @throws ParseException
	 * @throws IOException
	 * @throws SAXException
	 * @throws ParserConfigurationException
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	public String getIt(String json) throws URISyntaxException, ParseException, SAXException, IOException, ParserConfigurationException {
		
		final String config_file_name = "config.xml";
		URL config_pathUrl = this.getClass().getClassLoader().getResource(config_file_name);
		File fXmlFile = new File(config_pathUrl.getPath());
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(fXmlFile.getAbsolutePath());
		doc.getDocumentElement().normalize();
		NodeList nList = doc.getElementsByTagName("jira-config");

		String jiraBaseURL = ((Element)nList.item(0)).getElementsByTagName("url").item(0).getTextContent().trim();
		String jiraPassword = ((Element)nList.item(0)).getElementsByTagName("password").item(0).getTextContent().trim();
		String jiraUser = ((Element)nList.item(0)).getElementsByTagName("username").item(0).getTextContent().trim();

		// Setup the JRJC
		JiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		URI jiraServerUri = null;
		try {
			jiraServerUri = new URI(jiraBaseURL);
		} catch (URISyntaxException e) {
			;
		}
		
		JiraRestClient client = factory.createWithBasicHttpAuthentication(
				jiraServerUri, jiraUser, jiraPassword);

		// Issue issue = client.getIssueClient().getIssue("CMS-253").claim();

		JSONParser parser = new JSONParser();
		JSONObject json1 = (JSONObject) parser.parse(json);
		JSONArray commit_arr = (JSONArray) json1.get("commits");

		String coments = null;

		for (int i = 0; i < commit_arr.size(); i++) {
			JSONObject temp_commit = (JSONObject) commit_arr.get(i);
			List<String> mayJiraIssue = getIssueId(temp_commit.get("message")
					.toString());
			for (String jiraIssue : mayJiraIssue) {
				if (isJiraIssue(client, jiraIssue)) {
					coments = ((JSONObject) temp_commit.get("author")).get(
							"email").toString()
							+ "提交了 "
							+ temp_commit.get("url").toString()
							+ "\r\n"
							+ "提交日志为：\r\n"
							+ temp_commit.get("message").toString();
					Issue issue = client.getIssueClient().getIssue(jiraIssue)
							.claim();
					client.getIssueClient()
							.addComment(issue.getCommentsUri(),
									Comment.valueOf(coments)).claim();
				}
			}
		}
		return coments;
		// return x;
	}

	private List<String> getIssueId(String message) {
		List<String> multi_jira_issue = new ArrayList<String>();

		String temp_message = message.replace("\n", "##").replace("\r", "##");
		Pattern p = Pattern.compile("([A-Z]{2,10}-\\d+)", Pattern.MULTILINE);
		Matcher m = p.matcher(temp_message);

		while (m.find()) {
			multi_jira_issue.add(m.group());
		}
		;
		Set<String> hs = new HashSet<>();
		hs.addAll(multi_jira_issue);
		multi_jira_issue.clear();
		multi_jira_issue.addAll(hs);

		return multi_jira_issue;
	}

	private boolean isJiraIssue(JiraRestClient client, String jiraIssueId) {
		try {
			Issue issue = client.getIssueClient().getIssue(jiraIssueId).claim();
		} catch (RestClientException e) {
			return false;
		}
		return true;
	}
}