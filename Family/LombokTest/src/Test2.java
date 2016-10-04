
public class Test2 {

	interface FamilyA {
		interface Exp { int eval(); }
		interface Lit extends Exp { default int eval() { return 0; } }
	}
	
	interface FamilyB extends FamilyA {
		interface Exp extends FamilyA.Exp { int eval(); } // Field is copied.
		// interface Lit extends FamilyA.Lit, Exp {} // Conflict here.
	}
	
}
