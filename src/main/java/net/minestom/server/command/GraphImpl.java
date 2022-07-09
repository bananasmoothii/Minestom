package net.minestom.server.command;

import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.Argument;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

import static net.minestom.server.command.builder.arguments.ArgumentType.Literal;

record GraphImpl(Node root) implements Graph {

    record ConversionNode(Map<Argument<?>, ConversionNode> next) {
        ConversionNode() {
            this(new HashMap<>());
        }
    }

    static GraphImpl fromCommand(Command command) {
        final ConversionNode root = commandToNode(command);
        BuilderImpl builder = new BuilderImpl(Literal(command.getName()));
        for (var test : root.next.entrySet()) recursiveConversion(test, builder);
        return builder.build();
    }

    static void recursiveConversion(Map.Entry<Argument<?>, ConversionNode> entry, Builder builder) {
        builder.append(entry.getKey(), b -> {
            for (var e : entry.getValue().next.entrySet()) recursiveConversion(e, b);
        });
    }

    static ConversionNode commandToNode(Command command) {
        ConversionNode root = new ConversionNode();
        for (var syntax : command.getSyntaxes()) {
            ConversionNode syntaxNode = root;
            for (Argument<?> arg : syntax.getArguments()) {
                ConversionNode tmp = syntaxNode.next.get(arg);
                if (tmp == null) {
                    tmp = new ConversionNode();
                    syntaxNode.next.put(arg, tmp);
                }
                syntaxNode = tmp;
            }
        }
        return root;
    }

    static Graph merge(Collection<Command> commands) {
        BuilderImpl builder = new BuilderImpl(Literal(""));
        for (Command command : commands) {
            final ConversionNode node = commandToNode(command);
            builder.append(Literal(command.getName()), b -> {
                for (var e : node.next().entrySet()) recursiveConversion(e, b);
            });
        }
        return builder.build();
    }

    static GraphImpl merge(List<Graph> graphs) {
        BuilderImpl builder = new BuilderImpl(Literal(""));
        for (Graph graph : graphs) {
            recursiveMerge(graph.root(), builder);
        }
        return builder.build();
    }

    @Override
    public boolean compare(@NotNull Graph graph, @NotNull Comparator comparator) {
        // We currently do not include execution data in the graph
        return equals(graph);
    }

    static void recursiveMerge(Node node, Builder builder) {
        final List<Node> args = node.next();
        if (args.isEmpty()) {
            builder.append(node.argument());
        } else {
            builder.append(node.argument(), b -> {
                for (var arg : args) recursiveMerge(arg, b);
            });
        }
    }

    static final class BuilderImpl implements Graph.Builder {
        private final Argument<?> argument;
        private final List<BuilderImpl> children;

        public BuilderImpl(Argument<?> argument, List<BuilderImpl> children) {
            this.argument = argument;
            this.children = children;
        }

        public BuilderImpl(Argument<?> argument) {
            this(argument, new ArrayList<>());
        }

        @Override
        public Graph.@NotNull Builder append(@NotNull Argument<?> argument, @NotNull Consumer<Graph.Builder> consumer) {
            BuilderImpl builder = new BuilderImpl(argument);
            consumer.accept(builder);
            this.children.add(builder);
            return this;
        }

        @Override
        public Graph.@NotNull Builder append(@NotNull Argument<?> argument) {
            this.children.add(new BuilderImpl(argument, List.of()));
            return this;
        }

        @Override
        public @NotNull GraphImpl build() {
            final Node root = builderToNode(this);
            return new GraphImpl(root);
        }
    }

    static Node builderToNode(BuilderImpl builder) {
        final List<BuilderImpl> children = builder.children;
        Node[] nodes = new NodeImpl[children.size()];
        for (int i = 0; i < children.size(); i++) nodes[i] = builderToNode(children.get(i));
        return new NodeImpl(builder.argument, List.of(nodes));
    }

    record NodeImpl(Argument<?> argument, List<Graph.Node> next) implements Graph.Node {
    }
}
