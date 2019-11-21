package uk.co.gresearch.nortem.configeditor.configstore;

import org.adrianwalker.multilinestring.Multiline;
import org.junit.Assert;
import org.junit.Test;
import uk.co.gresearch.nortem.configeditor.model.ConfigEditorFile;

import java.util.ArrayList;
import java.util.List;

public class TestCaseConfigInfoProviderTest {
    /**
     * {
     *   "test_case_name": "test_case",
     *   "version": 12345,
     *   "author": "john",
     *   "config_name": "syslog",
     *   "description": "unitest test case",
     *   "test_specification": {
     *     "secret": true
     *   },
     *   "assertions": [
     *     {
     *       "assertion_type": "path_and_value_matches",
     *       "json_path": "$.a",
     *       "expected_pattern": "^.*mp$",
     *       "negated_pattern": false,
     *       "description": "match string",
     *       "active": true
     *     },
     *     {
     *       "assertion_type": "only_if_path_exists",
     *       "json_path": "s",
     *       "expected_pattern": "secret",
     *       "negated_pattern": true,
     *       "description": "skipped assertion",
     *       "active": false
     *     }
     *   ]
     * }
     */
    @Multiline
    public static String testCase;

    /**
     * {
     *   "test_case_name": "test_case",
     *   "version": 0,
     *   "author": "john",
     *   "config_name": "syslog",
     *   "description": "unitest test case",
     *   "test_specification": {
     *     "secret": true
     *   },
     *   "assertions": [
     *     {
     *       "assertion_type": "path_and_value_matches",
     *       "json_path": "$.a",
     *       "expected_pattern": "^.*mp$",
     *       "negated_pattern": false,
     *       "description": "match string",
     *       "active": true
     *     },
     *     {
     *       "assertion_type": "only_if_path_exists",
     *       "json_path": "s",
     *       "expected_pattern": "secret",
     *       "negated_pattern": true,
     *       "description": "skipped assertion",
     *       "active": false
     *     }
     *   ]
     * }
     */
    @Multiline
    public static String testCaseNew;

    /**
     * {
     *   "test_case_name": "./../../test",
     *   "version": 1,
     *   "author": "john",
     *   "config_name": "syslog",
     *   "description": "unitest test case",
     *   "test_specification": {
     *     "secret": true
     *   },
     *   "assertions": [
     *     {
     *       "assertion_type": "path_and_value_matches",
     *       "json_path": "$.a",
     *       "expected_pattern": "^.*mp$",
     *       "negated_pattern": false,
     *       "description": "match string",
     *       "active": true
     *     },
     *     {
     *       "assertion_type": "only_if_path_exists",
     *       "json_path": "s",
     *       "expected_pattern": "secret",
     *       "negated_pattern": true,
     *       "description": "skipped assertion",
     *       "active": false
     *     }
     *   ]
     * }
     */
    @Multiline
    public static String maliciousTestCase;
    
    public static String user = "steve@secret.net";
    private final TestCaseInfoProvider infoProvider = new TestCaseInfoProvider();

    @Test
    public void testCaseTestChangeAuthor() {
        ConfigInfo info = infoProvider.getConfigInfo(user, testCase);
        Assert.assertEquals(12345, info.getOldVersion());
        Assert.assertEquals(12346, info.getVersion());
        Assert.assertEquals("steve", info.getCommitter());
        Assert.assertEquals("Updating test case: syslog-test_case to version: 12346", info.getCommitMessage());

        Assert.assertEquals("steve", info.getCommitter());
        Assert.assertEquals(user, info.getCommitterEmail());

        Assert.assertEquals(1, info.getFilesContent().size());
        Assert.assertTrue(info.getFilesContent().containsKey("syslog-test_case.json"));
        Assert.assertTrue(info.getFilesContent()
                .get("syslog-test_case.json").indexOf("\"version\": 12346,") > 0);
        Assert.assertTrue(info.getFilesContent()
                .get("syslog-test_case.json").indexOf("\"author\": \"steve\",") > 0);
        Assert.assertFalse(info.isNewConfig());
        Assert.assertEquals(ConfigInfoType.TEST_CASE, info.getConfigInfoType());
    }


    @Test
    public void testCaseTestTestUnchangedAuthor() {
        ConfigInfo info = infoProvider.getConfigInfo("john@secret.net", testCase);
        Assert.assertEquals(12345, info.getOldVersion());
        Assert.assertEquals("john", info.getCommitter());
        Assert.assertEquals("Updating test case: syslog-test_case to version: 12346", info.getCommitMessage());
        Assert.assertEquals("john@secret.net", info.getCommitterEmail());
        Assert.assertEquals(1, info.getFilesContent().size());
        Assert.assertTrue(info.getFilesContent().containsKey("syslog-test_case.json"));
        Assert.assertTrue(info.getFilesContent()
                .get("syslog-test_case.json").indexOf("\"version\": 12346,") > 0);
        Assert.assertTrue(info.getFilesContent()
                .get("syslog-test_case.json").indexOf("\"author\": \"john\",") > 0);
        Assert.assertFalse(info.isNewConfig());
        Assert.assertEquals(ConfigInfoType.TEST_CASE, info.getConfigInfoType());
    }


    @Test
    public void testCaseNew() {
        ConfigInfo info = infoProvider.getConfigInfo(user, testCaseNew);
        Assert.assertEquals(info.getOldVersion(), 0);
        Assert.assertEquals(info.getCommitter(), "steve");
        Assert.assertEquals(info.getCommitMessage(), "Adding new test case: syslog-test_case");
        Assert.assertEquals(info.getCommitterEmail(), user);
        Assert.assertEquals(1, info.getFilesContent().size());
        Assert.assertTrue(info.getFilesContent().containsKey("syslog-test_case.json"));
        Assert.assertTrue(info.getFilesContent()
                .get("syslog-test_case.json").indexOf("\"version\": 1,") > 0);
        Assert.assertTrue(info.getFilesContent()
                .get("syslog-test_case.json").indexOf("\"author\": \"steve\",") > 0);
        Assert.assertTrue(info.isNewConfig());
        Assert.assertEquals(ConfigInfoType.TEST_CASE, info.getConfigInfoType());
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testCaseWrongJson() {
        ConfigInfo info = infoProvider.getConfigInfo(user, "WRONG JSON");
    }


    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testCaseWrongMissingMetadata() {
        ConfigInfo info = infoProvider.getConfigInfo(user, maliciousTestCase);
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testCaseWrongMissingMetadata2() {
        ConfigInfo info = infoProvider.getConfigInfo(user,
                testCase.replace("config_name", "undefined"));
    }

    @Test(expected = java.lang.IllegalArgumentException.class)
    public void testCaseWrongUser() {
        ConfigInfo info = infoProvider.getConfigInfo("INVALID", testCase);
    }

    @Test(expected = java.lang.UnsupportedOperationException.class)
    public void testCaseReleaseTest() {
        ConfigInfo info = infoProvider.getReleaseInfo("steve@secret.net", "");
    }

    @Test
    public void testCasefilterTest() {
        Assert.assertTrue(infoProvider.isStoreFile("abc.json"));
        Assert.assertFalse(infoProvider.isStoreFile("json.txt"));
    }

    @Test(expected = java.lang.UnsupportedOperationException.class)
    public void RulesVersionTest() {
        infoProvider.getReleaseVersion(new ArrayList<>());
    }
}

