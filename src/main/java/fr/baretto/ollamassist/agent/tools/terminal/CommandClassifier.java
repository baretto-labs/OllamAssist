package fr.baretto.ollamassist.agent.tools.terminal;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies a shell command into a {@link CommandTier} based on keyword and flag analysis.
 *
 * Rules are evaluated in order: DESTRUCTIVE first, then MUTATING, then READ_ONLY.
 * Any unrecognised command defaults to MUTATING (safe fallback — requires confirmation).
 */
public final class CommandClassifier {

    // -------------------------------------------------------------------------
    // DESTRUCTIVE patterns — always blocked
    // -------------------------------------------------------------------------

    private static final List<Pattern> DESTRUCTIVE_PATTERNS = List.of(
            // rm with recursive/force combos (short and long flags)
            pattern("\\brm\\b.*-[a-z]*r[a-z]*f|\\brm\\b.*-[a-z]*f[a-z]*r"),
            pattern("\\brm\\s+-rf\\b|\\brm\\s+-fr\\b"),
            pattern("\\brm\\b.*(--recursive|--force)"),
            // find with -delete flag (indirect mass deletion)
            pattern("\\bfind\\b.*-delete\\b"),
            pattern("\\bfind\\b.*-exec\\s+rm\\b"),
            // git force operations (short and long flags)
            pattern("\\bgit\\b.*\\bpush\\b.*(--force|-f)(?:\\s|$)"),
            pattern("\\bgit\\b.*\\breset\\b.*--hard"),
            pattern("\\bgit\\b.*\\bclean\\b.*-[a-z]*f"),
            // drop / truncate databases
            pattern("\\bdrop\\s+(?:table|database|schema)\\b"),
            pattern("\\btruncate\\s+table\\b"),
            // format / mkfs
            pattern("\\bmkfs\\b"),
            pattern("\\bformat\\s+[a-z]:\\\\"),
            // shred / secure-delete
            pattern("\\bshred\\b"),
            pattern("\\bsrm\\b"),
            // dd to raw device
            pattern("\\bdd\\b.*of=/dev/[a-z]"),
            // fork bomb guard
            pattern(":\\(\\)\\s*\\{"),
            // chmod/chown -R on root
            pattern("\\bchmod\\b.*-[a-z]*R[a-z]*\\s+[0-7]{3,4}\\s+/\\s"),
            pattern("\\bchown\\b.*-[a-z]*R[a-z]*\\s+\\S+\\s+/\\s"),
            // xargs with rm (indirect deletion)
            pattern("\\bxargs\\b.*\\brm\\b"),
            // pipe to shell interpreter — remote code execution risk
            pattern("\\|\\s*(sh|bash|zsh|dash|ksh|python3?|ruby|perl|node)\\b"),
            // command substitution via backticks or $() — allows arbitrary injection
            pattern("`[^`\n]+`"),
            pattern("\\$\\([^)\n]+\\)")
    );

    // -------------------------------------------------------------------------
    // Output redirection to sensitive targets — always DESTRUCTIVE
    // Must be checked before READ_ONLY so that a READ_ONLY command (cat, grep, find…)
    // that redirects its output to /dev/, /etc/, /proc/, or /sys/ is correctly blocked.
    // -------------------------------------------------------------------------

    private static final List<Pattern> DESTRUCTIVE_REDIRECT_PATTERNS = List.of(
            // Redirect (write or append) to a raw device
            pattern(">\\s*/dev/"),
            // Redirect to system configuration directories
            pattern(">\\s*/etc/"),
            pattern(">\\s*/proc/"),
            pattern(">\\s*/sys/"),
            // Redirect to /root/ or /boot/
            pattern(">\\s*/root/"),
            pattern(">\\s*/boot/")
    );

    // -------------------------------------------------------------------------
    // READ_ONLY patterns — execute without confirmation
    // -------------------------------------------------------------------------

    private static final List<Pattern> READ_ONLY_PATTERNS = List.of(
            // filesystem inspection
            pattern("^\\s*ls(\\s|$)"),
            pattern("^\\s*find\\b"),
            pattern("^\\s*cat\\b"),
            pattern("^\\s*head\\b"),
            pattern("^\\s*tail\\b"),
            pattern("^\\s*less\\b"),
            pattern("^\\s*more\\b"),
            pattern("^\\s*file\\b"),
            pattern("^\\s*stat\\b"),
            pattern("^\\s*wc\\b"),
            pattern("^\\s*du\\b"),
            pattern("^\\s*df\\b"),
            pattern("^\\s*pwd\\b"),
            pattern("^\\s*env\\b"),
            pattern("^\\s*printenv\\b"),
            // search
            pattern("^\\s*grep\\b"),
            pattern("^\\s*rg\\b"),
            pattern("^\\s*ag\\b"),
            pattern("^\\s*ack\\b"),
            // git read operations
            pattern("^\\s*git\\s+(status|log|diff|show|describe|shortlog|branch|tag|remote\\s+[-v]|stash\\s+list|ls-files|rev-parse|blame|check-ignore|config\\s+--list|config\\s+--get)\\b"),
            // build/test read operations
            pattern("^\\s*./gradlew\\s+(test|check|dependencies|projects|tasks)\\b"),
            pattern("^\\s*./mvnw?\\s+(test|verify|dependency:tree|help:)\\b"),
            pattern("^\\s*mvn\\s+(test|verify|dependency:tree|help:)\\b"),
            pattern("^\\s*gradle\\s+(test|check|dependencies|projects|tasks)\\b"),
            pattern("^\\s*npm\\s+(test|run\\s+test|ls|list|outdated|audit)\\b"),
            pattern("^\\s*yarn\\s+(test|list|audit|why)\\b"),
            // misc read
            pattern("^\\s*which\\b"),
            pattern("^\\s*type\\b"),
            pattern("^\\s*man\\b"),
            pattern("^\\s*help\\b"),
            pattern("^\\s*history\\b"),
            pattern("^\\s*date\\b"),
            pattern("^\\s*whoami\\b"),
            pattern("^\\s*id\\b"),
            pattern("^\\s*uname\\b"),
            pattern("^\\s*ps\\b"),
            pattern("^\\s*top\\b"),
            pattern("^\\s*htop\\b"),
            pattern("^\\s*lsof\\b"),
            pattern("^\\s*netstat\\b"),
            pattern("^\\s*ping\\b"),
            pattern("^\\s*nslookup\\b"),
            pattern("^\\s*dig\\b")
    );

    private CommandClassifier() {
    }

    /**
     * Classifies {@code command} into a {@link CommandTier}.
     *
     * @param command the full shell command string (may be multi-word)
     * @return the appropriate tier; never {@code null}
     */
    public static CommandTier classify(String command) {
        if (command == null || command.isBlank()) {
            return CommandTier.READ_ONLY;
        }
        String normalized = command.trim().toLowerCase(Locale.ROOT);

        for (Pattern p : DESTRUCTIVE_PATTERNS) {
            if (p.matcher(normalized).find()) {
                return CommandTier.DESTRUCTIVE;
            }
        }

        // Check output redirection to sensitive targets — takes priority over READ_ONLY match
        for (Pattern p : DESTRUCTIVE_REDIRECT_PATTERNS) {
            if (p.matcher(normalized).find()) {
                return CommandTier.DESTRUCTIVE;
            }
        }

        // If the command contains any output redirection, it cannot be READ_ONLY.
        // A command like "cat file > output.txt" modifies state even if "cat" alone is read-only.
        boolean hasRedirection = normalized.contains(">>");
        if (!hasRedirection) {
            // Check for single > that is not part of >> (already checked above)
            // Simple heuristic: contains ">" outside of ">>" patterns
            String withoutDoubleArrow = normalized.replace(">>", "\u0000\u0000");
            hasRedirection = withoutDoubleArrow.contains(">");
        }

        for (Pattern p : READ_ONLY_PATTERNS) {
            if (p.matcher(normalized).find()) {
                // A READ_ONLY command with output redirection becomes MUTATING
                return hasRedirection ? CommandTier.MUTATING : CommandTier.READ_ONLY;
            }
        }

        return CommandTier.MUTATING;
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }
}
