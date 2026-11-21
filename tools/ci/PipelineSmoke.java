import dev.koral.pipeline.CompilerPipeline;
import java.io.File;
import java.nio.file.Files;

/** Exercise the installed library entry point without invoking a CLI handler. */
public final class PipelineSmoke {
    public static void main(String[] args) throws Exception {
        File source = new File(args[0]);
        File output = new File(args[1]);
        File sdk = new File(args[2]);
        File cliOutput = new File(args[3]);
        var summary = CompilerPipeline.INSTANCE.check(source);
        var result = CompilerPipeline.INSTANCE.build(source, output, sdk);
        if (!summary.equals(result.getSummary()) || summary.getInstructionCount() == 0) {
            throw new AssertionError("Library check/build disagree");
        }
        for (String name : new String[]{"counter.c", "counter.abi.json", "counter.so"}) {
            if (Files.mismatch(new File(output, name).toPath(), new File(cliOutput, name).toPath()) != -1) {
                throw new AssertionError("Library/CLI output differs: " + name);
            }
        }
        File bad = new File(output.getParentFile(), "bad.kt");
        Files.writeString(bad.toPath(), "fun unsupported(count: ULong): ULong = count\n");
        boolean rejected = false;
        try {
            CompilerPipeline.INSTANCE.build(bad, output, sdk);
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().contains("Missing trusted @Instruction")) throw expected;
            rejected = true;
        }
        if (!rejected) throw new AssertionError("Invalid contract was accepted");
        for (String name : new String[]{"counter.so", "counter.abi.json", "counter.build.json"}) {
            if (new File(output, name).exists()) throw new AssertionError("Stale success artifact: " + name);
        }
        System.out.println("Installed JVM pipeline: check/build parity and failure invalidation passed");
    }
}
