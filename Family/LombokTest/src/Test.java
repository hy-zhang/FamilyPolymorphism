import java.util.ArrayList;
import java.util.List;
import lombok.Obj;

public class Test {
	public static void main(String[] args) {
	      PC.Add e1 = PC.Add.of(PC.Lit.of(4), PC.Lit.of(3));
	      System.out.println(e1._print() + " = " + e1._eval() + ", Literals = " + e1._collectLit().toString());
	}
}

//BaseFeature
@Obj
interface B {
	interface Exp { int _eval(); }
	interface Lit extends Exp {
		int x();
		default int _eval() { return x(); }
	}
	interface Add extends Exp {
		Exp e1(); Exp e2();
		default int _eval() { return e1()._eval() + e2()._eval(); }
	}
}

//SubFeature
@Obj
interface S extends B {
	interface Sub extends Exp {
		Exp e1(); Exp e2();
		default int _eval() { return e1()._eval() - e2()._eval(); }
	}
}

//PrintFeature
@Obj
interface P extends B {
	interface Exp { String _print(); }
	interface Lit {
		default String _print() { return "" + x(); }
	}
	interface Add {
		default String _print() {
			return e1()._print() + " + " + e2()._print();
		}
	}
}

//CollectFeature
@Obj
interface C extends B {
	interface Exp { List<Integer> _collectLit(); }
	interface Lit {
		default List<Integer> _collectLit() {
			List<Integer> list = new ArrayList<Integer>(1);
			list.add(x());
			return list;
		}
	}
	interface Add {
		default List<Integer> _collectLit() { 
			List<Integer> list = new ArrayList<Integer>();
			list.addAll(e1()._collectLit());
			list.addAll(e2()._collectLit());
			return list; 
		}
	}
}

//Print + Collect Feature
@Obj
interface PC extends P, C {}
