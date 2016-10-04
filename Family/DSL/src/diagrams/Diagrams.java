package diagrams;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import lombok.Obj;

@Obj
interface Family {
    interface Shape {
        Extent toExtent();
        XML toXML(List<Attr> styleAttrs, Transform trans);
        default Drawing draw(StyleSheet style) {
            return Drawing.of(singletonList(this), singletonList(style), singletonList(Identity.of()));
        }
        String show();
    }

    interface Rectangle extends Shape {
        double width();
        double height();
        default Extent toExtent() {
            return Extent.of(Pos.of(width(), height()).resize(-0.5), Pos.of(width(), height()).resize(0.5));
        }
        default XML toXML(List<Attr> styleAttrs, Transform trans) {
            List<Attr> attrs = new ArrayList<>(Pos.of(width(), height()).resize(-0.5).transform(trans).toAttrs("x", "y"));
            attrs.addAll(Pos.of(width(), height()).toAttrs("width", "height"));
            attrs.addAll(styleAttrs);
            return XML.of(attrs, "rect", emptyList());
        }
        default String show() {
            return "Rectangle " + width() + " " + height();
        }
    }

    interface Ellipse extends Shape {
        double rx();
        double ry();
        default Extent toExtent() {
            return Extent.of(Pos.of(-rx(), -ry()), Pos.of(rx(), ry()));
        }
        default XML toXML(List<Attr> styleAttrs, Transform trans) {
            List<Attr> attrs = new ArrayList<>(Pos.of(0, 0).transform(trans).toAttrs("cx", "cy"));
            attrs.addAll(Pos.of(rx(), ry()).toAttrs("rx", "ry"));
            attrs.addAll(styleAttrs);
            return XML.of(attrs, "ellipse", emptyList());
        }
        default String show() {
            return "Ellipse " + rx() + " " + ry();
        }
    }
    interface Triangle extends Shape {
        double length();
        default Extent toExtent() {
            double y = Math.sqrt(3)/4 * length();
            return Extent.of(Pos.of(-length()/2, -y), Pos.of(length()/2, y));
        }
        default XML toXML(List<Attr> styleAttrs, Transform trans) {
            double h = Math.sqrt(3)/4 * length();
            List<Attr> attrs = new ArrayList<>();
            attrs.add(Attr.of("points", Stream.of(Pos.of(-length()/2, -h), Pos.of(length()/2, -h), Pos.of(0, h))
                    .map(pos -> pos.transform(trans).show())
                    .reduce("", (s1, s2) -> s1 + " " + s2)));
            attrs.addAll(styleAttrs);
            return XML.of(attrs, "polygon", emptyList());
        }
        default String show() {
            return "Triangle " + length();
        }
    }

    interface StyleSheet {
        List<Styling> stylings();
        default List<Attr> toAttrs() {
            boolean hasFill = false;
            List<Attr> attrs = new ArrayList<>();
            for (Styling s : stylings()) {
                Attr attr = s.toAttr();
                if (attr.name().equals("fill")) hasFill = true;
                attrs.add(attr);
            }
            if (!hasFill) attrs.add(Attr.of("fill", "none"));
            return attrs;
        }
    }
    interface Styling {
        Attr toAttr();
    }
    interface FillColor extends Styling {
        Color color();
        default Attr toAttr() {
            return Attr.of("fill", color().show());
        }
    }
    interface StrokeColor extends Styling {
        Color color();
        default Attr toAttr() {
            return Attr.of("stroke", color().show());
        }
    }
    interface StrokeWidth extends Styling {
        double width();
        default Attr toAttr() {
            return Attr.of("stroke-width", ""+width());
        }
    }

    interface Color {
        String show();
    }
    interface Red extends Color {
        default String show() { return "red"; }
    }
    interface Blue extends Color {
        default String show() { return "blue"; }
    }
    interface Green extends Color {
        default String show() { return "green"; }
    }
    interface Yellow extends Color {
        default String show() { return "yellow"; }
    }
    interface Bisque extends Color {
        default String show() { return "bisque"; }
    }
    interface Black extends Color {
        default String show() { return "black"; }
    }

    interface Picture {
        Drawing draw();
        String show();
    }
    interface Place extends Picture {
        StyleSheet style();
        Shape shape();
        default Drawing draw() {
            return shape().draw(style());
        }
        default String show() {
            return "(Place " + shape().show() + ")";
        }
    }
    interface Above extends Picture {
        Picture p1();
        Picture p2();
        default Drawing draw() {
            Drawing d1 = p1().draw();
            Drawing d2 = p2().draw();
            Extent e1 = d1.toExtent();
            Extent e2 = d2.toExtent();
            Drawing t1 = d1.transform(Translate.of(Pos.of(0, e2.p2().y())));
            Drawing t2 = d2.transform(Translate.of(Pos.of(0, e1.p1().y())));
            return t1.merge(t2);
        }
        default String show() {
            return "(Above " + p1().show() + " " + p2().show() + ")";
        }
    }
    interface Beside extends Picture {
        Picture p1();
        Picture p2();
        default Drawing draw() {
            Drawing d1 = p1().draw();
            Drawing d2 = p2().draw();
            Drawing t1 = d1.transform(Translate.of(Pos.of(d2.toExtent().p1().x(), 0)));
            Drawing t2 = d2.transform(Translate.of(Pos.of(d1.toExtent().p2().x(), 0)));
            return t1.merge(t2);
        }
        default String show() {
            return "(Beside " + p1().show() + " " + p2().show() + ")";
        }
    }


    interface Pos {
        double x();
        double y();
        default Pos transform(Transform t) {
            return t.transform(this);
        }
        default Pos add(Pos other) {
            return Pos.of(x() + other.x(), y() + other.y());
        }
        default Pos resize(double scale) {
            return Pos.of(scale*x(), scale*y());
        }
        default String show() {
            return x() + "," + y();
        }
        default List<Attr> toAttrs(String x, String y) {
            return asList(Attr.of(x, ""+x()), Attr.of(y, ""+y()));
        }
    }
    interface Extent {
        Pos p1();
        Pos p2();
        default Extent union(Extent e) {
            return Extent.of(Pos.of(Math.min(p1().x(), e.p1().x()), Math.min(p1().y(), e.p1().y())),
                    Pos.of(Math.max(p2().x(), e.p2().x()), Math.max(p2().y(), e.p2().y())));
        }
        default Extent transform(Transform t) {
            return Extent.of(p1().transform(t), p2().transform(t));
        }
        default String show() {
            return "(" + p1().show() + "," + p2().show() + ")";
        }
    }
    interface Drawing {
        List<Transform> transforms();
        List<Shape> shapes();
        List<StyleSheet> styles();

        default Extent toExtent() {
            return IntStream.range(0, shapes().size())
                    .mapToObj(i -> shapes().get(i).toExtent().transform(transforms().get(i)))
                    .reduce(Extent::union).get();
        }
        default Drawing transform(Transform trans) {
            return Drawing.of(shapes(), styles(), transforms().stream().map(t1 -> Compose.of(trans, t1)).collect(toList()));
        }
        default Drawing merge(Drawing other) {
            return Drawing.of(concat(shapes(), other.shapes()), concat(styles(), other.styles()), concat(transforms(), other.transforms()));
        }
        default XML toXML() {
            int scale = 10;
            Extent e = toExtent();
            Pos p1 = e.p1();
            Pos p2 = e.p2();
            Pos p = Pos.of(p2.x()-p1.x(), p2.y()-p1.y()).resize(scale);
            List<Attr> svgAttrs = new ArrayList<>(p.toAttrs("width", "height"));
            svgAttrs.add(Attr.of("viewBox", p1.resize(scale).show() + "," + p.show()));
            svgAttrs.add(Attr.of("xmlns", "http://www.w3.org/2000/svg"));
            svgAttrs.add(Attr.of("version", "1.1"));

            List<XML> shapeXMLs = IntStream.range(0, shapes().size())
                            .mapToObj(i -> shapes().get(i).toXML(styles().get(i).toAttrs(), transforms().get(i)))
                            .collect(toList());
            return XML.of(svgAttrs, "svg", singletonList(XML.of(singletonList(Attr.of("transform", "scale(" + Pos.of(1,-1).resize(scale).show() + ")")),
                            "g", shapeXMLs)));
        }
        default String show() {
            return IntStream.range(0, shapes().size()).mapToObj(i -> "(" + transforms().get(i).show() + shapes().get(i).show() + ")").collect(joining(",", "[", "]"));
        }
    }

    interface Transform {
        Pos transform(Pos pos);
        String show();
    }
    interface Identity extends Transform {
        default Pos transform(Pos pos) {
            return pos;
        }
        default String show() {
            return "Identity";
        }
    }
    interface Translate extends Transform {
        Pos pos();
        default Pos transform(Pos pos) {
            return pos().add(pos);
        }
        default String show() {
            return "Translate " + pos().show();
        }
    }
    interface Compose extends Transform {
        Transform t1();
        Transform t2();
        default Pos transform(Pos pos) {
            return t1().transform(t2().transform(pos));
        }
        default String show() {
            return "Compose(" + t1().show() + "," + t2().show() + ")";
        }
    }

    interface XML {
        String tag();
        List<Attr> attrs();
        List<XML> xmls();

        default String show() {
            String attrs = " " + attrs().stream().map(Attr::show).collect(joining(" "));
            if (xmls().size() == 0)
                return "<" + tag() + attrs + "/>";
            else
                return "<" + tag() + attrs + ">\n" + xmls().stream().map(XML::show).collect(joining("\n")) + "\n</" + tag() + ">";
        }
    }
    interface Attr {
        String name();
        String value();
        default String show() {
            return name() + "=\"" + value() + "\"";
        }
    }
    static <E> List<E> concat(List<E> xs, List<E> ys) {
        List<E> tmp = new ArrayList<>(xs);
        tmp.addAll(ys);
        return tmp;
    }
    static Picture figure() {
        StyleSheet sheet = StyleSheet.of(asList(FillColor.of(Blue.of()), StrokeWidth.of(0)));
        Picture head = Place.of(Ellipse.of(3, 3), StyleSheet.of(asList(StrokeWidth.of(0.1), StrokeColor.of(Black.of()), FillColor.of(Bisque.of()))));
        Picture hands = Place.of(Rectangle.of(1, 10), StyleSheet.of(asList(FillColor.of(Red.of()), StrokeWidth.of(0))));
        Picture upper = Place.of(Triangle.of(10), StyleSheet.of(asList(FillColor.of(Red.of()), StrokeWidth.of(0))));
        Picture leg = Place.of(Rectangle.of(5, 1), sheet);
        Picture foot = Place.of(Rectangle.of(1, 2), sheet);
        Picture legs = Beside.of(Beside.of(leg, Place.of(Rectangle.of(5, 2), StyleSheet.of(asList(StrokeWidth.of(0))))), leg);
        Picture foots = Beside.of(Beside.of(foot, Place.of(Rectangle.of(1, 2), StyleSheet.of(asList(StrokeWidth.of(0))))), foot);
        Picture human = Above.of(Above.of(Above.of(Above.of(head, hands), upper), legs), foots);
        return human;
    }
}

public class Diagrams {
    public static void main(String[] args) {
        Path file = Paths.get("human.svg");
        Family.XML xml = Family.figure().draw().toXML();
        System.out.println(xml.show());
        try {
            Files.write(file, asList(xml.show()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}