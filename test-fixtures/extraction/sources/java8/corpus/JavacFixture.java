package corpus;

@Deprecated
public final class JavacFixture {
    public String message = "fixture";

    public Runnable lambda() {
        return () -> System.out.println(message);
    }
}
