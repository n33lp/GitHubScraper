package ca.ualberta.cs.cmput402.ghdow;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import static ca.ualberta.cs.cmput402.ghdow.MyGithub.calculateTimeDifference;
import static ca.ualberta.cs.cmput402.ghdow.MyGithub.milliToHour;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(MockitoJUnitRunner.class) class MyGithubTest {
    @Test
    void getIssueCreateDates() throws IOException {
        // We don't have a login token for github :(
        String token = "I am a fake token";
        MyGithub my = new MyGithub(token);
        assertNotNull(my);

        // We made this field protected instead of private so we can inject our mock
        // directly
        my.gitHub = mock(GitHub.class);

        // Set up a fake repository
        String fakeRepoName = "fakeRepo";
        GHRepository fakeRepo = mock(GHRepository.class);

        // Put our fake repository in a list of fake repositories
        // We made this field protected instead of private so we can inject our mock
        // directly, but we could have mocked GHMyself/GHPerson instead
        my.myRepos = new HashMap<>();
        my.myRepos.put(fakeRepoName, fakeRepo);

        // Generate some mock issues with mock dates for our mock repository
        final int DATES = 30;

        ArrayList<GHIssue> mockIssues = new ArrayList<>();
        ArrayList<Date> expectedDates = new ArrayList<>();
        HashMap<String, Date> issueToDate = new HashMap<>();
        Calendar calendar = Calendar.getInstance();
        for (int i = 1; i < DATES+1; i++) {
            calendar.set(100, Calendar.JANUARY, i, 1, 1, 1);
            Date issueDate = calendar.getTime();

            // Give this mock GHIssue a unique Mockito "name"
            // This has nothing to do with github, you can
            // give any mockito object a name
            String issueMockName = String.format("getIssueCreateDates issue #%d", i);
            GHIssue issue = mock(GHIssue.class, issueMockName);

            expectedDates.add(issueDate);
            mockIssues.add(issue);

            // Note that we DO NOT try to
            // when(issue.getCreatedAt())
            // because that's what causes the Mockito/github-api bug ...
            // instead we'll just save what we would have wanted to do
            // in a hashmap and then apply it later to GHIssueWrapper
            // which does not have the bug because it doesn't use
            // github-api 's WithBridgeMethods

            issueToDate.put(issueMockName, issueDate);
        }

        // Supply the mock repo with a list of mock issues to return
        when(fakeRepo.getIssues(GHIssueState.CLOSED)).thenReturn(mockIssues);

        List<Date> actualDates;

        // Inside the try block, Mockito will intercept GHIssueWrapper's constructor
        // and have it construct mock GHIssueWrappers instead
        // We have to use a try-with-resources, or it will get stuck like this and probably
        // ruin our other tests.
        try (MockedConstruction<GHIssueWrapper> ignored = mockConstruction(
                GHIssueWrapper.class,
                (mock, context) -> {
                    // Figure out which GHIssue the mock GHIssueWrapper 's
                    // constructor was called with
                    GHIssue issue = (GHIssue) context.arguments().get(0);
                    assertNotNull(issue);

                    // Ask mockito what name we gave the mock issue
                    String issueName = mockingDetails(issue)
                            .getMockCreationSettings()
                            .getMockName()
                            .toString();

                    // Make sure GHIssueWrapper was constructed with one of our mock
                    // GHIssue objects
                    assertTrue(issueToDate.containsKey(issueName));

                    // Get the date associated with the mock GHIssue object
                    // This is where we work around the Mockito/github-api bug!
                    Date date = issueToDate.get(issueName);
                    assertNotNull(date);
                    // Apply the date to the mock GHIssueWrapper
                    when(mock.getCreatedAt()).thenReturn(date);
                }
        )) {
            // This is the only line actually inside the try block
            actualDates = my.getIssueCreateDates();
        }

        // Check that we got our fake dates out
        assertEquals(expectedDates.size(), DATES);
        assertEquals(actualDates.size(), DATES);

        for (int i = 1; i < DATES; i++) {
            assertEquals(expectedDates.get(i), actualDates.get(i));
            System.out.println(expectedDates.get(i));
        }
    }

    @Test
    void testGetMostPopularDay() throws IOException {
        // Prepare mock data for GHCommit objects with different commit dates
        List<GHCommit> mockCommits = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.FEBRUARY, 21); // Set a Wed
        Date mondayDate = cal.getTime();

        for (int i = 0; i < 5; i++) {
            GHCommit mockCommit = mock(GHCommit.class);
            when(mockCommit.getCommitDate()).thenReturn(mondayDate);
            mockCommits.add(mockCommit);
        }

        cal.set(2024, Calendar.FEBRUARY, 22); // Set a Thur
        Date tuesdayDate = cal.getTime();

        for (int i = 0; i < 3; i++) {
            GHCommit mockCommit = mock(GHCommit.class);
            when(mockCommit.getCommitDate()).thenReturn(tuesdayDate);
            mockCommits.add(mockCommit);
        }

        // Set up the commits iterable in MyGithub to return the mock GHCommit objects
        MyGithub myGithub = new MyGithub("fakeToken");
        myGithub.commits = mockCommits;

        // Invoke the method
        String mostPopularDay = myGithub.getMostPopularDay();

        // Assert that the method returns the expected result based on the mock data
        assertEquals("Wednesday", mostPopularDay);
    }

    @Test
    void testGetAVGTIME() throws IOException {
        // Prepare mock data for GHCommit objects with different commit dates
        List<GHCommit> mockCommits = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        // Set the desired date and time
        cal.set(Calendar.YEAR, 2024);
        cal.set(Calendar.MONTH, Calendar.FEBRUARY); // Note that Calendar.MONTH is zero-based
        cal.set(Calendar.DAY_OF_MONTH, 21);
        cal.set(Calendar.HOUR_OF_DAY, 0); // Midnight
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date firstCommitDate = cal.getTime();
        int count = 0;
        for (int i = 0; i < 5; i++) {
            GHCommit mockCommit = mock(GHCommit.class);
            when(mockCommit.getCommitDate()).thenReturn(firstCommitDate);
            mockCommits.add(mockCommit);
            count++;
        }

        // Set the desired date and time
        cal.set(Calendar.YEAR, 2024);
        cal.set(Calendar.MONTH, Calendar.FEBRUARY); // Note that Calendar.MONTH is zero-based
        cal.set(Calendar.DAY_OF_MONTH, 23);
        cal.set(Calendar.HOUR_OF_DAY, 0); // Midnight
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Date lastCommitDate = cal.getTime();

        for (int i = 0; i < 3; i++) {
            GHCommit mockCommit = mock(GHCommit.class);
            when(mockCommit.getCommitDate()).thenReturn(lastCommitDate);
            mockCommits.add(mockCommit);
            count++;
        }

        // Set up the commits iterable in MyGithub to return the mock GHCommit objects
        MyGithub myGithub = new MyGithub("fakeToken");
        myGithub.firstCommitDate = firstCommitDate;
        myGithub.lastCommitDate = lastCommitDate;
        myGithub.commitCount = count;


        assertEquals(48.0, milliToHour(calculateTimeDifference(firstCommitDate,lastCommitDate)));

        myGithub.commits = mockCommits;

        // Invoke the method
        String avgTime = myGithub.getAVGTIME();

        // Assert
        assertEquals(String.valueOf(milliToHour(calculateTimeDifference(firstCommitDate,lastCommitDate))/count), avgTime); // Assuming the expected average time is 24 hours
    }

    @Test
    void testGetAverageIssueOpenTime() throws IOException {
        // Prepare mock closed issues with specific creation and closing dates
        List<GHIssue> mockIssues = new ArrayList<>();
        Date creationDate1 = new Date();  // Set the creation date as the current date
        Date closedDate1 = new Date(creationDate1.getTime() + 24 * 60 * 60 * 1000);  // Add 1 day to the creation date
        GHIssue mockIssue1 = mock(GHIssue.class);
        when(mockIssue1.getCreatedAt()).thenReturn(creationDate1);
        when(mockIssue1.getClosedAt()).thenReturn(closedDate1);
        mockIssues.add(mockIssue1);

        // Add more mock issues as needed

        // Set up the mock repositories with the prepared closed issues
        List<GHRepository> mockRepos = new ArrayList<>();
        GHRepository mockRepo = mock(GHRepository.class);
        when(mockRepo.getIssues(GHIssueState.CLOSED)).thenReturn(mockIssues);
        mockRepos.add(mockRepo);

        // Create a MyGithub instance and set the mock repositories
        MyGithub myGithub = new MyGithub("fakeToken");
        myGithub.myRepos = new HashMap<>();
        myGithub.myRepos.put("mockRepo", mockRepo);

        // Invoke the method
        float averageOpenTime = myGithub.getAverageIssueOpenTime();

        // Assert the result
        assertEquals(24.0f, averageOpenTime);  // Assuming the issue was open for 1 day (24 hours)
    }


    @Test
    void testGetAveragePullRequestOpenTime() throws IOException {
        // Prepare mock data for closed pull requests with specific creation and closing dates
        List<GHPullRequest> mockPullRequests = new ArrayList<>();
        // Mock pull request 1: Open for 2 days
        GHPullRequest mockPR1 = mock(GHPullRequest.class);
        try {
            when(mockPR1.getCreatedAt()).thenReturn(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse("2024-02-20T00:00:00Z")); // February 20, 2024
            when(mockPR1.getClosedAt()).thenReturn(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse("2024-02-22T00:00:00Z")); // February 22, 2024
        } catch (ParseException e) {
            e.printStackTrace();
        }
        mockPullRequests.add(mockPR1);
        // Mock pull request 2: Open for 1 day
        GHPullRequest mockPR2 = mock(GHPullRequest.class);
        try {
            when(mockPR2.getCreatedAt()).thenReturn(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse("2024-02-23T00:00:00Z")); // February 23, 2024
            when(mockPR2.getClosedAt()).thenReturn(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse("2024-02-24T00:00:00Z")); // February 24, 2024
        } catch (ParseException e) {
            e.printStackTrace();
        }
        mockPullRequests.add(mockPR2);

        // Set up mock repository with the prepared closed pull requests
        GHRepository mockRepo = mock(GHRepository.class);
        when(mockRepo.getPullRequests(GHIssueState.CLOSED)).thenReturn(mockPullRequests);

        // Set up mock MyGithub instance with the mock repository
        MyGithub myGithub = new MyGithub("fakeToken");
        myGithub.myRepos = new HashMap<>();
        myGithub.myRepos.put("mockRepo", mockRepo);

        // Invoke the method
        float averageOpenTime = myGithub.getAveragePullRequestOpenTime();

        // Calculate the expected average open time (in hours)
        float expectedAverageOpenTime = (2 * 24 + 1 * 24) / 2.0f; // Average of 2 days and 1 day

        // Assert the result
        assertEquals(expectedAverageOpenTime, averageOpenTime);
    }

    @Test
    void testGetAverageBranchCount() throws IOException {
        // Prepare mock data for branches
        Map<String, GHBranch> mockBranches = new HashMap<>();
        // Mock branch 1
        GHBranch mockBranch1 = mock(GHBranch.class);
        // Mock branch 2
        GHBranch mockBranch2 = mock(GHBranch.class);
        // Add branches to the map
        mockBranches.put("branch1", mockBranch1);
        mockBranches.put("branch2", mockBranch2);

        // Set up mock repository with the prepared branches
        GHRepository mockRepo = mock(GHRepository.class);
        // Mock the behavior to return the map of branches when getBranches() is called
        when(mockRepo.getBranches()).thenReturn(mockBranches);

        // Set up mock MyGithub instance with the mock repository
        MyGithub myGithub = new MyGithub("fakeToken");
        myGithub.myRepos = new HashMap<>();
        myGithub.myRepos.put("mockRepo", mockRepo);

        // Invoke the method
        float averageBranchCount = myGithub.getAverageBranchCount();

        // Calculate the expected average branch count
        float expectedAverageBranchCount = mockBranches.size();

        // Assert the result
        assertEquals(expectedAverageBranchCount, averageBranchCount);
    }

    @Test
    void testGetRepos() throws IOException {
        // Mock GHRepository objects
        GHRepository repo1 = mock(GHRepository.class);
        GHRepository repo2 = mock(GHRepository.class);
        // Create a list of repositories
        List<GHRepository> repositories = Arrays.asList(repo1, repo2);

        // Mock MyGithub object
        MyGithub myGithub = mock(MyGithub.class);

        // Stub the getRepos() method to return the list of repositories
        when(myGithub.getRepos()).thenReturn(repositories);

        // Call the method under test
        List<GHRepository> actualRepositories = myGithub.getRepos();

        // Assert that the returned list is not null
        assertNotNull(actualRepositories);

        // Assert that the returned list contains the expected repositories
        assertEquals(2, actualRepositories.size());
        assertTrue(actualRepositories.contains(repo1));
        assertTrue(actualRepositories.contains(repo2));
    }


}

