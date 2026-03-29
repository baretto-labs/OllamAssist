package fr.baretto.ollamassist.agent;

import fr.baretto.ollamassist.agent.tools.terminal.CommandClassifier;
import fr.baretto.ollamassist.agent.tools.terminal.CommandTier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandClassifierTest {

    // -------------------------------------------------------------------------
    // READ_ONLY
    // -------------------------------------------------------------------------

    @Test
    void ls_isReadOnly() {
        assertThat(CommandClassifier.classify("ls -la")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void catFile_isReadOnly() {
        assertThat(CommandClassifier.classify("cat src/main/Foo.java")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void gitStatus_isReadOnly() {
        assertThat(CommandClassifier.classify("git status")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void gitLog_isReadOnly() {
        assertThat(CommandClassifier.classify("git log --oneline -10")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void gitDiff_isReadOnly() {
        assertThat(CommandClassifier.classify("git diff HEAD~1")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void gradlewTest_isReadOnly() {
        assertThat(CommandClassifier.classify("./gradlew test")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void npmTest_isReadOnly() {
        assertThat(CommandClassifier.classify("npm test")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void grep_isReadOnly() {
        assertThat(CommandClassifier.classify("grep -r 'TODO' src/")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void curl_isReadOnly() {
        assertThat(CommandClassifier.classify("curl https://example.com/api")).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void nullCommand_isReadOnly() {
        assertThat(CommandClassifier.classify(null)).isEqualTo(CommandTier.READ_ONLY);
    }

    @Test
    void blankCommand_isReadOnly() {
        assertThat(CommandClassifier.classify("   ")).isEqualTo(CommandTier.READ_ONLY);
    }

    // -------------------------------------------------------------------------
    // MUTATING
    // -------------------------------------------------------------------------

    @Test
    void gitCommit_isMutating() {
        assertThat(CommandClassifier.classify("git commit -m 'feat: add feature'")).isEqualTo(CommandTier.MUTATING);
    }

    @Test
    void gitCheckout_isMutating() {
        assertThat(CommandClassifier.classify("git checkout -b feature/foo")).isEqualTo(CommandTier.MUTATING);
    }

    @Test
    void mkdir_isMutating() {
        assertThat(CommandClassifier.classify("mkdir -p src/new/package")).isEqualTo(CommandTier.MUTATING);
    }

    @Test
    void npmInstall_isMutating() {
        assertThat(CommandClassifier.classify("npm install lodash")).isEqualTo(CommandTier.MUTATING);
    }

    @Test
    void gradlewBuild_isMutating() {
        assertThat(CommandClassifier.classify("./gradlew build")).isEqualTo(CommandTier.MUTATING);
    }

    @Test
    void unknownCommand_isMutating() {
        assertThat(CommandClassifier.classify("some-custom-script --run")).isEqualTo(CommandTier.MUTATING);
    }

    // -------------------------------------------------------------------------
    // DESTRUCTIVE
    // -------------------------------------------------------------------------

    @Test
    void rmRf_isDestructive() {
        assertThat(CommandClassifier.classify("rm -rf /tmp/mydir")).isEqualTo(CommandTier.DESTRUCTIVE);
    }

    @Test
    void rmFr_isDestructive() {
        assertThat(CommandClassifier.classify("rm -fr target/")).isEqualTo(CommandTier.DESTRUCTIVE);
    }

    @Test
    void gitPushForce_isDestructive() {
        assertThat(CommandClassifier.classify("git push origin main --force")).isEqualTo(CommandTier.DESTRUCTIVE);
    }

    @Test
    void gitPushForceShort_isDestructive() {
        assertThat(CommandClassifier.classify("git push -f origin main")).isEqualTo(CommandTier.DESTRUCTIVE);
    }

    @Test
    void gitResetHard_isDestructive() {
        assertThat(CommandClassifier.classify("git reset --hard HEAD~3")).isEqualTo(CommandTier.DESTRUCTIVE);
    }

    @Test
    void dropTable_isDestructive() {
        assertThat(CommandClassifier.classify("DROP TABLE users")).isEqualTo(CommandTier.DESTRUCTIVE);
    }

    @Test
    void truncateTable_isDestructive() {
        assertThat(CommandClassifier.classify("truncate table events")).isEqualTo(CommandTier.DESTRUCTIVE);
    }
}
