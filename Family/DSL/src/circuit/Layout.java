package circuit;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.function.IntUnaryOperator;

import lombok.Obj;

@Obj
public interface Layout {
    List<List<IntPair>> layout();
    default Layout map(IntUnaryOperator f) {
            return Layout.of(layout().stream()
                    .map(xs -> xs.stream()
                            .map(pr -> pr.map(f))
                            .collect(toList()))
                    .collect(toList()));
    }
}