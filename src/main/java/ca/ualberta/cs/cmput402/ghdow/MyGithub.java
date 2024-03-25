package ca.ualberta.cs.cmput402.ghdow;

import java.io.IOException;

import org.apache.commons.lang3.tuple.Pair;

import org.kohsuke.github.*;
import java.util.Date;
import java.util.*;

public class MyGithub {
    protected GitHub gitHub;
    protected GHPerson myself;
    protected Map<String, GHRepository> myRepos;
    private List<GHCommit> myCommits;

    public Date firstCommitDate;
    public Date lastCommitDate;

    public int commitCount;

    public Iterable<? extends GHCommit> commits;

    public void init() throws IOException {
        Pair<Iterable<? extends GHCommit>, Integer> result = getCommits();
        commits = result.getLeft();
        commitCount = result.getRight();

        firstCommitDate = null;
        lastCommitDate = null;

        for (GHCommit commit : commits) {
            Date date = commit.getCommitDate();

            if (firstCommitDate == null || date.before(firstCommitDate)) {
                firstCommitDate = date;
            }

            if (lastCommitDate == null || date.after(lastCommitDate)) {
                lastCommitDate = date;
            }
        }
    }

    public MyGithub(String token) throws IOException {
        gitHub = new GitHubBuilder().withOAuthToken(token).build();
    }

    private GHPerson getMyself() throws IOException {
        if (myself == null) {
            myself = gitHub.getMyself();
        }
        return myself;
    }

    public String getGithubName() throws IOException {
        return gitHub.getMyself().getLogin();
    }

    public List<GHRepository> getRepos() throws IOException {

        int maxAttempts = 3;
        int attempt = 0;
        IOException lastException = null;
        while (attempt < maxAttempts) {
            attempt++;


            try {
                if (myRepos == null) {
                    myRepos = getMyself().getRepositories();
                }
                return new ArrayList<>(myRepos.values());
            } catch (IOException e1) {
                System.out.println("Error occurred while getting repositories (Attempt " + attempt + "): " + e1.getMessage());
                lastException = e1;
                try {
                    Thread.sleep(1000); // Wait for 1 second before retrying
                } catch (InterruptedException e2) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw lastException != null ? lastException : new IOException("Failed to get repositories after " + maxAttempts + " attempts.");
    }

    static private int argMax(int[] days) {
        int max = Integer.MIN_VALUE;
        int arg = -1;
        for (int i = 0; i < days.length; i++) {
            if (days[i] > max) {
                max = days[i];
                arg = i;
            }
        }
        return arg;
    }

    static private String intToDay(int day) {
        return switch (day) {
            case Calendar.SUNDAY -> "Sunday";
            case Calendar.MONDAY -> "Monday";
            case Calendar.TUESDAY -> "Tuesday";
            case Calendar.WEDNESDAY -> "Wednesday";
            case Calendar.THURSDAY -> "Thursday";
            case Calendar.FRIDAY -> "Friday";
            case Calendar.SATURDAY -> "Saturday";
            default -> throw new IllegalArgumentException("Not a day: " + day);
        };
    }

    public String getMostPopularDay() throws IOException {
        System.out.println("getting most POP day");
        final int SIZE = 8;
        int[] days = new int[SIZE];
        Calendar cal = Calendar.getInstance();

        for (GHCommit commit: commits) {
            Date date = commit.getCommitDate();

//            System.out.println(commit.getCommitShortInfo().getMessage() + " -- " + date.toString()); // to visualize

            cal.setTime(date);
            int day = cal.get(Calendar.DAY_OF_WEEK);
            days[day] += 1;
        }
        return intToDay(argMax(days));
    }

    protected Pair<Iterable<? extends GHCommit>, Integer> getCommits() throws IOException {
        int count = 0;
        if (myCommits == null) {
            myCommits = new ArrayList<>();

            for (GHRepository repo: getRepos()) {
                System.out.println("Loading commits: repo " + repo.getName());
                try {
                    for (GHCommit commit : repo.queryCommits().author(getGithubName()).list()) {
                        myCommits.add(commit);
                        count++;
                        if (count % 100 == 0) {
                            System.out.println("Loading commits: " + count);
                        }
                    }
                } catch (GHException e) {
                    if (!e.getCause().getMessage().contains("Repository is empty")) {
                        throw e;
                    }
                }
            }

        }

        return Pair.of(myCommits, count);
    }

    public ArrayList<Date> getIssueCreateDates() throws IOException {  // gets all the commits   needed for step 3
        ArrayList<Date> result = new ArrayList<>();
        for (GHRepository repo: getRepos()) {  // gets all the repos
            List<GHIssue> issues = repo.getIssues(GHIssueState.CLOSED);
            for (GHIssue issue: issues)  // gets all the issues
                result.add((new GHIssueWrapper(issue)).getCreatedAt());
            }
        return result;
    }

    public static float calculateTimeDifference(Date startDate, Date endDate) {
        // Calculate the time difference in milliseconds
        return endDate.getTime() - startDate.getTime();
    }

    public static float milliToHour(float time) {
        return time / 3600000;
    }

    public String getAVGTIME() throws IOException {
        float timeDifferenceMillis = calculateTimeDifference(firstCommitDate, lastCommitDate);  // milliseconds
        float timeDifferencehrs = milliToHour(timeDifferenceMillis);
        float avgTime = timeDifferencehrs/commitCount;
//        System.out.println("latest commit " + lastCommitDate);
//        System.out.println("first commit " + firstCommitDate);
//        System.out.println("total commit " + commitCount);
//        System.out.println("avg commit " + avgTime);

        return String.valueOf(avgTime);
    }

    public float getAverageIssueOpenTime() throws IOException {
        List<Long> openTimes = new ArrayList<>();

        // Get all closed issues from your repositories
        for (GHRepository repo : getRepos()) {
            List<GHIssue> issues = repo.getIssues(GHIssueState.CLOSED);

            // Calculate time difference for each closed issue
            for (GHIssue issue : issues) {
                if (issue.getClosedAt() != null && issue.getCreatedAt() != null) {
                    long openTime = issue.getClosedAt().getTime() - issue.getCreatedAt().getTime();
                    openTimes.add(openTime);
                }
            }
        }

        // Calculate the total open time
        long totalOpenTime = 0;
        for (long openTime : openTimes) {
            totalOpenTime += openTime;
        }

        // Calculate the average open time
        float averageOpenTime = (float) totalOpenTime / openTimes.size();

        // Convert milliseconds to hours
        averageOpenTime = milliToHour(averageOpenTime);

        return averageOpenTime;
    }

    public float getAveragePullRequestOpenTime() throws IOException {
        List<Long> openTimes = new ArrayList<>();

        // Get all pull requests from your repositories
        for (GHRepository repo : getRepos()) {
            List<GHPullRequest> pullRequests = repo.getPullRequests(GHIssueState.CLOSED);

            // Calculate time difference for each open pull request
            for (GHPullRequest pr : pullRequests) {
                if (pr.getCreatedAt() != null && pr.getClosedAt() != null) {
                    long openTime = pr.getClosedAt().getTime() - pr.getCreatedAt().getTime();
                    openTimes.add(openTime);
                }
            }
        }

        // Calculate the total open time
        float totalOpenTime = 0;
        for (long openTime : openTimes) {
            totalOpenTime += openTime;
        }

        // Calculate the average open time
        float averageOpenTime = totalOpenTime / openTimes.size();

        // Convert milliseconds to hours
        averageOpenTime = milliToHour(averageOpenTime);

        return averageOpenTime;
    }

    public float getAverageBranchCount() throws IOException {

        List<GHRepository> repos = getRepos();
        if (repos == null || repos.isEmpty()) {
            return 0.0f;
        }

        int totalBranchCount = 0;
        for (GHRepository repo : repos) {
            totalBranchCount += repo.getBranches().size();
        }

        return (float) totalBranchCount / repos.size();
    }
}

