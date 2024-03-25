package ca.ualberta.cs.cmput402.ghdow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    static String getOAuthToken() throws IOException {
        Path tokenFile = Paths.get(
                System.getProperty("user.home"),
                "githubOAuthToken.txt"
        );
        String token = Files.readString(tokenFile);
        return token.strip();
    }
    public static void main(String[] args) throws IOException {
        // Did get help from gpt 3.5
        String token = getOAuthToken();
        MyGithub my = new MyGithub(token);
        my.init();
        System.out.println("Logged in as " + my.getGithubName());
        System.out.println("Most often commits on: " + my.getMostPopularDay());
        System.out.println("Average time between commits: " + my.getAVGTIME() + " hrs");
        System.out.println("Average time issues stay open: " + my.getAverageIssueOpenTime() + " hrs");
        System.out.println("Average time pull requests stay open: " + my.getAveragePullRequestOpenTime() + " hrs");
        System.out.println("Average branches: " + my.getAverageBranchCount());
    }
}