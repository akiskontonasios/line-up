package lineup.util;

public class Tuple<A, B> {
	public final A _1;
	public final B _2;

	public Tuple(A a, B b) {
		this._1 = a;
		this._2 = b;
	}

	public A getA() {
		return _1;
	}

	public B getB() {
		return _2;
	}

	@Override
	public String toString() {
		return String.format("(%s, %s)", getA().toString(), getB().toString());
	}
}
