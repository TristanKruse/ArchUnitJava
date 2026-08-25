package corpus;

public record ModernFixture(String value) implements Runnable {
    @Override
    public void run() {
        System.out.println(value);
    }
}
