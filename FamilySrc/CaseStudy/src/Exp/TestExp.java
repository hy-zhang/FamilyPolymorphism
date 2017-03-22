package Exp;

import lombok.Family;

public class TestExp {

}

interface FamilyA {
	interface Exp {
		int eval();
	}
	interface Lit extends Exp {
		int _i();
		default int eval() { return _i(); }
	}
	interface Add extends Exp {
		Exp _e1(); Exp _e2();
		default int eval() { return _e1().eval() + _e2().eval(); }
	}
}

@Family
interface FamilyB extends FamilyA {
	interface Exp {
		String print();
	}
	interface Lit {
		default String print() { return "" + _i(); }
	}
	interface Add {
		default String print() { return _e1().print() + " + " + _e2().print(); }
	}
}

@Family
interface FamilyC extends FamilyA, FamilyB {}
