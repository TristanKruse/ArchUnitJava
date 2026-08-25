package corpus;

public sealed interface Shape permits Circle {
}

final class Circle implements Shape {
}
