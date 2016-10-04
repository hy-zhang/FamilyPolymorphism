import java.util.ArrayList;
import lombok.Obj;

public class Test5 {

	public static void main(String[] args) {

		for (int i = 1; i <= 50; i++) {
			System.out.println("------ i = " + i + " ------\n" + Family_PrettyPrinter.Tree.print(i) + "\n-------------------\n\n");	
		}

	}
	
}

@Obj
interface Family_Doc {
	interface Doc {}
	interface Nil extends Doc {}
	interface Text extends Doc { String s(); Doc d(); }
	interface Line extends Doc { int i(); Doc d(); }
}

@Obj
interface Family_Doc_LayoutFits extends Family_Doc {
	interface Doc { String _layout(); boolean _fits(int w); }
	interface Nil extends Doc {
		default String _layout() { return ""; }
		default boolean _fits(int w) {
			if (w < 0) return false;
			return true;
		}
	}
	interface Text extends Doc {
		default String _layout() { return s() + d()._layout(); }
		default boolean _fits(int w) {
			if (w < 0) return false;
			return d()._fits(w - s().length());
		}
	}
	interface Line extends Doc {
		default String _layout() {
			String indent = new String(new char[i()]).replace("\0", " ");
			return "\n" + indent + d()._layout();
		}
		default boolean _fits(int w) {
			if (w < 0) return false;
			return true;
		}
	}
}

@Obj
interface Family_Document {
	interface Document {}
	interface DNil extends Document {}
	interface DConcat extends Document { Document d1(); Document d2(); }
	interface DNest extends Document { int i(); Document d(); }
	interface DText extends Document { String s(); }
	interface DLine extends Document {}
	interface DUnion extends Document { Document d1(); Document d2(); }
}

@Obj
interface Family_Document_Flatten extends Family_Document {
	interface Document {
		Document _flatten();
		default Document _group() { return DUnion.of(this._flatten(), this); }
	}
	interface DNil extends Document {
		default Document _flatten() { return DNil.of(); }
	}
	interface DConcat extends Document {
		default Document _flatten() { return DConcat.of(d1()._flatten(), d2()._flatten()); }
	}
	interface DNest extends Document {
		default Document _flatten() { return DNest.of(d()._flatten(), i()); }
	}
	interface DText extends Document {
		default Document _flatten() { return DText.of(s()); }
	}
	interface DLine extends Document {
		default Document _flatten() { return DText.of(" "); }
	}
	interface DUnion extends Document {
		default Document _flatten() { return d1()._flatten(); }
	}
}

@Obj
interface Family_PrettyPrinter extends Family_Document_Flatten, Family_Doc_LayoutFits {
	interface Pair { int x(); Document y(); }
	static Doc be(int w, int k, ArrayList<Pair> z) {
		if (z.isEmpty()) return Nil.of();
		Pair p = z.get(0);
		ArrayList<Pair> z2 = new ArrayList<Pair> ();
		z2.addAll(z); z2.remove(0);
		return p.y()._beaux(w, k, p.x(), z2);
	}
	interface Document {
		Doc _beaux(int w, int k, int i, ArrayList<Pair> z);
		default Doc _best(int w, int k) {
			ArrayList<Pair> z = new ArrayList<Pair>();
			z.add(Pair.of(0, this));
			return be(w, k, z);
		}
		default String _pretty(int w) {
			return this._best(w, 0)._layout();
		}
		/* Repeated code. */
		Document _flatten();
		default Document _group() { return DUnion.of(this._flatten(), this); }
	}
	interface DNil extends Document {
		default Doc _beaux(int w, int k, int i, ArrayList<Pair> z) {
			return be(w, k, z);
		}
		default Document _flatten() { return DNil.of(); }
	}
	interface DConcat extends Document {
		default Doc _beaux(int w, int k, int i, ArrayList<Pair> z) {
			ArrayList<Pair> z2 = new ArrayList<Pair>();
			z2.add(Pair.of(i, d1()));
			z2.add(Pair.of(i, d2()));
			z2.addAll(z);
			return be(w, k, z2);
		}
		default Document _flatten() { return DConcat.of(d1()._flatten(), d2()._flatten()); }
	}
	interface DNest extends Document {
		default Doc _beaux(int w, int k, int i, ArrayList<Pair> z) {
			ArrayList<Pair> z2 = new ArrayList<Pair>();
			z2.add(Pair.of(i + i(), d()));
			z2.addAll(z);
			return be(w, k, z2);
		}
		default Document _flatten() { return DNest.of(d()._flatten(), i()); }
	}
	interface DText extends Document {
		default Doc _beaux(int w, int k, int i, ArrayList<Pair> z) {
			return Text.of(be(w, k + s().length(), z), s());
		}
		default Document _flatten() { return DText.of(s()); }
	}
	interface DLine extends Document {
		default Doc _beaux(int w, int k, int i, ArrayList<Pair> z) {
			return Line.of(be(w, i, z), i);
		}
		default Document _flatten() { return DText.of(" "); }
	}
	interface DUnion extends Document {
		default Doc _beaux(int w, int k, int i, ArrayList<Pair> z) {
			ArrayList<Pair> z2 = new ArrayList<Pair>();
			z2.add(Pair.of(i, d1()));
			z2.addAll(z);
			ArrayList<Pair> z3 = new ArrayList<Pair>();
			z3.add(Pair.of(i, d2()));
			z3.addAll(z);
			Doc x = be(w, k, z2);
			Doc y = be(w, k, z3);
			if (x._fits(w - k)) return x;
			else return y;
		}
		default Document _flatten() { return d1()._flatten(); }
	}
	
	interface Tree {
		String s(); ArrayList<Tree> subTrees();
		default Document showTree() {
			Document showBracket = null;
			if (subTrees().isEmpty()) {
				showBracket = DNil.of();
			} else {
				Document showTrees = null;
				showTrees = subTrees().get(0).showTree();
				for (int i = 1; i < subTrees().size(); i++)
					showTrees = DConcat.of(DConcat.of(DConcat.of(showTrees, DText.of(",")), DLine.of()), subTrees().get(i).showTree());
				showBracket = DConcat.of(DConcat.of(DText.of("["), DNest.of(showTrees, 1)), DText.of("]"));
			}
			return DConcat.of(DText.of(s()), DNest.of(showBracket, s().length()))._group(); // Cast is removed (with repeated code).
		}
		static Tree buildTree() {
			ArrayList<Tree> ts = new ArrayList<Tree>();
			ArrayList<Tree> tts = new ArrayList<Tree>();
			ArrayList<Tree> ttts = new ArrayList<Tree>();
			tts.add(Tree.of("ccc", new ArrayList<Tree>()));
			tts.add(Tree.of("dd", new ArrayList<Tree>()));
			ttts.add(Tree.of("gg", new ArrayList<Tree>()));
			ttts.add(Tree.of("hhh", new ArrayList<Tree>()));
			ttts.add(Tree.of("ii", new ArrayList<Tree>()));
			ts.add(Tree.of("bbbbb", tts));
			ts.add(Tree.of("eee", new ArrayList<Tree>()));
			ts.add(Tree.of("ffff", ttts));
			return Tree.of("aaa", ts);
		}
		
		static String print(int w) {
			return buildTree().showTree()._pretty(w);
		}
	}
	
}

