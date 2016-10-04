import lombok.Obj;

public class Test4 {
	
	public static void main(String[] args) {
		
		FamilyD.run();
		
	}
	
}

@Obj
interface FamilyA {
	interface Doc {}
	interface Nil extends Doc {}
	interface Text extends Doc { String s(); }
	interface Concat extends Doc { Doc d1(); Doc d2(); }
}

@Obj
interface FamilyB extends FamilyA {
	interface Line extends Doc {}
	interface Nest extends Doc { int x(); Doc d(); }
}

@Obj
interface FamilyC extends FamilyA {
	interface Doc { String _layout(); }
	interface Nil extends Doc { default String _layout() { return ""; } }
	interface Text extends Doc { default String _layout() { return s(); } }
	interface Concat extends Doc {
		default String _layout() { return d1()._layout() + d2()._layout(); }
	}
}

@Obj
interface FamilyD extends FamilyB, FamilyC {
	interface Line extends Doc {
		default String _layout() { return "\n"; }
	}
	interface Nest extends Doc {
		default String _layout() {
			String indent = new String(new char[x()]).replace("\0", " ");
			return d()._layout().replaceAll("\n", "\n" + indent);
		}
	}
	static void run() {
		Concat c = Concat.of(Text.of("aa"), Nest.of(Concat.of(Line.of(), Concat.of(Text.of("bbb"), Concat.of(Line.of(), Text.of("ccc")))), 2));
		System.out.println(c._layout());
	}
}
