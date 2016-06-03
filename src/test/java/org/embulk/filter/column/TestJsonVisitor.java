package org.embulk.filter.column;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.embulk.filter.column.ColumnFilterPlugin.ColumnConfig;
import org.embulk.filter.column.ColumnFilterPlugin.PluginTask;

import org.embulk.EmbulkTestRuntime;
import org.embulk.config.ConfigLoader;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskSource;
import org.embulk.spi.Column;
import org.embulk.spi.Exec;
import org.embulk.spi.FileInput;
import org.embulk.spi.ParserPlugin;
import org.embulk.spi.Schema;
import org.embulk.spi.SchemaConfig;
import org.embulk.spi.type.Type;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.msgpack.value.MapValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

import static org.embulk.spi.type.Types.BOOLEAN;
import static org.embulk.spi.type.Types.DOUBLE;
import static org.embulk.spi.type.Types.JSON;
import static org.embulk.spi.type.Types.LONG;
import static org.embulk.spi.type.Types.STRING;
import static org.embulk.spi.type.Types.TIMESTAMP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class TestJsonVisitor {
    @Rule
    public EmbulkTestRuntime runtime = new EmbulkTestRuntime();

    @Before
    public void createReasource()
    {
        // config = config().set("type", "column");
    }

    private ConfigSource config()
    {
        return runtime.getExec().newConfigSource();
    }

    private Schema schema(Column... columns)
    {
        return new Schema(Lists.newArrayList(columns));
    }

    private ConfigSource configFromYamlString(String... lines)
    {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append("\n");
        }
        String yamlString = builder.toString();

        ConfigLoader loader = new ConfigLoader(Exec.getModelManager());
        return loader.fromYamlString(yamlString);
    }

    private PluginTask taskFromYamlString(String... lines)
    {
        ConfigSource config = configFromYamlString(lines);
        return config.loadConfig(PluginTask.class);
    }

    private JsonVisitor jsonVisitor(PluginTask task, Schema inputSchema)
    {
        Schema outputSchema = ColumnFilterPlugin.buildOutputSchema(task, inputSchema);
        return new JsonVisitor(task, inputSchema, outputSchema);
    }

    @Test
    public void buildShouldVisitSet()
    {
        PluginTask task = taskFromYamlString(
                "type: column",
                "columns:",
                "  - {name: \"$.json1.a.a.a\"}",
                "add_columns:",
                "  - {name: \"$.json1.b.b[1].b\", type: string, default: foo}",
                "drop_columns:",
                "  - {name: \"$.json1.c.c[*].c\"}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        assertTrue(subject.shouldVisit("$.json1.a.a.a"));
        assertTrue(subject.shouldVisit("$.json1.a.a"));
        assertTrue(subject.shouldVisit("$.json1.a"));
        assertTrue(subject.shouldVisit("$.json1.b.b[1].b"));
        assertTrue(subject.shouldVisit("$.json1.b.b[1]"));
        assertTrue(subject.shouldVisit("$.json1.b.b"));
        assertTrue(subject.shouldVisit("$.json1.b"));
        assertTrue(subject.shouldVisit("$.json1.c.c[*].c"));
        assertTrue(subject.shouldVisit("$.json1.c.c[*]"));
        assertTrue(subject.shouldVisit("$.json1.c.c"));
        assertTrue(subject.shouldVisit("$.json1.c"));
        assertTrue(subject.shouldVisit("$.json1"));
        assertFalse(subject.shouldVisit("$.json2"));
    }

    @Test
    public void buildJsonSchema_DropColumns()
    {
        PluginTask task = taskFromYamlString(
                "type: column",
                "drop_columns:",
                "  - {name: $.json1.a.default}",
                "  - {name: $.json1.a.copy}",
                "  - {name: \"$.json1.a.copy_array[1]\"}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        assertFalse(subject.jsonDropColumns.containsKey("$.json1"));
        assertTrue(subject.jsonDropColumns.containsKey("$.json1.a"));
        assertTrue(subject.jsonDropColumns.containsKey("$.json1.a.copy_array"));

        {
            HashSet<String> jsonColumns = subject.jsonDropColumns.get("$.json1.a");
            assertEquals(2, jsonColumns.size());
            assertTrue(jsonColumns.contains("$.json1.a.default"));
            assertTrue(jsonColumns.contains("$.json1.a.copy"));
        }

        {
            HashSet<String> jsonColumns = subject.jsonDropColumns.get("$.json1.a.copy_array");
            assertEquals(1, jsonColumns.size());
            assertTrue(jsonColumns.contains("$.json1.a.copy_array[1]"));
        }
    }

    @Test
    public void buildJsonSchema_AddColumns()
    {
        PluginTask task = taskFromYamlString(
                "type: column",
                "add_columns:",
                "  - {name: $.json1.a.default, type: string, default: foo}",
                "  - {name: $.json1.a.copy, src: $.json1.a.src}",
                "  - {name: \"$.json1.a.copy_array[1]\", src: \"$.json1.a.copy_array[0]\"}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        assertFalse(subject.jsonAddColumns.containsKey("$.json1"));
        assertTrue(subject.jsonAddColumns.containsKey("$.json1.a"));
        assertTrue(subject.jsonAddColumns.containsKey("$.json1.a.copy_array"));

        {
            HashMap<String, JsonColumn> jsonColumns = subject.jsonAddColumns.get("$.json1.a");
            assertEquals(2, jsonColumns.size());
            String[] keys = jsonColumns.keySet().toArray(new String[0]);
            JsonColumn[] values = jsonColumns.values().toArray(new JsonColumn[0]);
            assertEquals("$.json1.a.default", keys[0]);
            assertEquals("$.json1.a.default", values[0].getPath());
            assertEquals("$.json1.a.copy", keys[1]);
            assertEquals("$.json1.a.copy", values[1].getPath());
        }

        {
            HashMap<String, JsonColumn> jsonColumns = subject.jsonAddColumns.get("$.json1.a.copy_array");
            assertEquals(1, jsonColumns.size());
            String[] keys = jsonColumns.keySet().toArray(new String[0]);
            JsonColumn[] values = jsonColumns.values().toArray(new JsonColumn[0]);
            assertEquals("$.json1.a.copy_array[1]", keys[0]);
            assertEquals("$.json1.a.copy_array[1]", values[0].getPath());
        }
    }

    @Test
    public void buildJsonSchema_Columns()
    {
        PluginTask task = taskFromYamlString(
                "type: column",
                "columns:",
                "  - {name: $.json1.a.default, type: string, default: foo}",
                "  - {name: $.json1.a.copy, src: $.json1.a.src}",
                "  - {name: \"$.json1.a.copy_array[1]\", src: \"$.json1.a.copy_array[0]\"}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        assertFalse(subject.jsonColumns.containsKey("$.json1"));
        assertTrue(subject.jsonColumns.containsKey("$.json1.a"));
        assertTrue(subject.jsonColumns.containsKey("$.json1.a.copy_array"));

        {
            HashMap<String, JsonColumn> jsonColumns = subject.jsonColumns.get("$.json1.a");
            assertEquals(2, jsonColumns.size());
            String[] keys = jsonColumns.keySet().toArray(new String[0]);
            JsonColumn[] values = jsonColumns.values().toArray(new JsonColumn[0]);
            assertEquals("$.json1.a.default", keys[0]);
            assertEquals("$.json1.a.default", values[0].getPath());
            assertEquals("$.json1.a.copy", keys[1]);
            assertEquals("$.json1.a.copy", values[1].getPath());
        }

        {
            HashMap<String, JsonColumn> jsonColumns = subject.jsonColumns.get("$.json1.a.copy_array");
            assertEquals(1, jsonColumns.size());
            String[] keys = jsonColumns.keySet().toArray(new String[0]);
            JsonColumn[] values = jsonColumns.values().toArray(new JsonColumn[0]);
            assertEquals("$.json1.a.copy_array[1]", keys[0]);
            assertEquals("$.json1.a.copy_array[1]", values[0].getPath());
        }
    }

    @Test
    public void buildJsonSchema_Mix() {
        PluginTask task = taskFromYamlString(
                "type: column",
                "drop_columns:",
                "  - {name: $.json1.a.default}",
                "add_columns:",
                "  - {name: $.json1.a.copy, src: $.json1.a.src}",
                "columns:",
                "  - {name: \"$.json1.a.copy_array[1]\", src: \"$.json1.a.copy_array[0]\"}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        assertFalse(subject.jsonDropColumns.isEmpty());
        assertFalse(subject.jsonAddColumns.isEmpty());
        assertTrue(subject.jsonColumns.isEmpty()); // drop_columns overcome columns
    }

    @Test
    public void visitMap_DropColumns() {
        PluginTask task = taskFromYamlString(
                "type: column",
                "drop_columns:",
                "  - {name: $.json1.k1.k1}",
                "  - {name: $.json1.k2}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        // {"k1":{"k1":"v"},"k2":{"k2":"v"}}
        Value k1 = ValueFactory.newString("k1");
        Value k2 = ValueFactory.newString("k2");
        Value v = ValueFactory.newString("v");
        Value map = ValueFactory.newMap(
                k1, ValueFactory.newMap(k1, v),
                k2, ValueFactory.newMap(k2, v));

        MapValue visited = subject.visit("$.json1", map).asMapValue();
        assertEquals("{\"k1\":{}}", visited.toString());
    }

    @Test
    public void visitMap_AddColumns() {
        PluginTask task = taskFromYamlString(
                "type: column",
                "add_columns:",
                "  - {name: $.json1.k3, type: json, default: \"{}\"}",
                "  - {name: $.json1.k3.k3, type: string, default: v}",
                "  - {name: $.json1.k4, src: $.json1.k2}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        // {"k1":{"k1":"v"},"k2":{"k2":"v"}}
        Value k1 = ValueFactory.newString("k1");
        Value k2 = ValueFactory.newString("k2");
        Value v = ValueFactory.newString("v");
        Value map = ValueFactory.newMap(
                k1, ValueFactory.newMap(k1, v),
                k2, ValueFactory.newMap(k2, v));

        MapValue visited = subject.visit("$.json1", map).asMapValue();
        assertEquals("{\"k1\":{\"k1\":\"v\"},\"k2\":{\"k2\":\"v\"},\"k3\":{\"k3\":\"v\"},\"k4\":{\"k2\":\"v\"}}", visited.toString());
    }

    @Test
    public void visitMap_Columns() {
        PluginTask task = taskFromYamlString(
                "type: column",
                "columns:",
                "  - {name: $.json1.k1}",
                "  - {name: $.json1.k2.k2}", // $.json1.k2 must be specified now, or $.json.k2 will be removed entirely
                "  - {name: $.json1.k3, type: json, default: \"{}\"}",
                "  - {name: $.json1.k3.k3, type: string, default: v}",
                "  - {name: $.json1.k4, src: $.json1.k2}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        // {"k1":{"k1":"v"},"k2":{"k1":"v","k2":"v"}}
        Value k1 = ValueFactory.newString("k1");
        Value k2 = ValueFactory.newString("k2");
        Value v = ValueFactory.newString("v");
        Value map = ValueFactory.newMap(
                k1, ValueFactory.newMap(k1, v),
                k2, ValueFactory.newMap(k2, v));

        MapValue visited = subject.visit("$.json1", map).asMapValue();
        assertEquals("{\"k1\":{\"k1\":\"v\"},\"k3\":{\"k3\":\"v\"},\"k4\":{\"k2\":\"v\"}}", visited.toString());
    }

    @Test
    public void visitArray_DropColumns() {
        PluginTask task = taskFromYamlString(
                "type: column",
                "drop_columns:",
                "  - {name: \"$.json1.k1[0].k1\"}",
                "  - {name: \"$.json1.k2[*]\"}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        // {"k1":[{"k1":"v"}[,"k2":["v","v"]}
        Value k1 = ValueFactory.newString("k1");
        Value k2 = ValueFactory.newString("k2");
        Value v = ValueFactory.newString("v");
        Value map = ValueFactory.newMap(
                k1, ValueFactory.newArray(ValueFactory.newMap(k1, v)),
                k2, ValueFactory.newArray(v, v));

        MapValue visited = subject.visit("$.json1", map).asMapValue();
        assertEquals("{\"k1\":[{}],\"k2\":[]}", visited.toString());
    }

    @Test
    public void visitArray_AddColumns() {
        PluginTask task = taskFromYamlString(
                "type: column",
                "add_columns:",
                "  - {name: \"$.json1.k1[1]\", src: \"$.json1.k1[0]\"}",
                "  - {name: \"$.json1.k3\", type: json, default: \"[]\"}",
                "  - {name: \"$.json1.k3[0]\", type: json, default: \"{}\"}",
                "  - {name: \"$.json1.k3[0].k3\", type: string, default: v}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        // {"k1":[{"k1":"v"}],"k2":["v","v"]}
        Value k1 = ValueFactory.newString("k1");
        Value k2 = ValueFactory.newString("k2");
        Value v = ValueFactory.newString("v");
        Value map = ValueFactory.newMap(
                k1, ValueFactory.newArray(ValueFactory.newMap(k1, v)),
                k2, ValueFactory.newArray(v, v));

        MapValue visited = subject.visit("$.json1", map).asMapValue();
        assertEquals("{\"k1\":[{\"k1\":\"v\"},{\"k1\":\"v\"}],\"k2\":[\"v\",\"v\"],\"k3\":[{\"k3\":\"v\"}]}", visited.toString());
    }

    @Test
    public void visitArray_Columns() {
        PluginTask task = taskFromYamlString(
                "type: column",
                "columns:",
                "  - {name: \"$.json1.k1\"}",
                "  - {name: \"$.json1.k1[1]\", src: \"$.json1.k1[0]\"}",
                "  - {name: \"$.json1.k2[0]\"}", // $.json1.k2 must be specified now, or $.json.k2 will be removed entirely
                "  - {name: \"$.json1.k3\", type: json, default: \"[]\"}",
                "  - {name: \"$.json1.k3[0]\", type: json, default: \"{}\"}",
                "  - {name: \"$.json1.k3[0].k3\", type: string, default: v}");
        Schema inputSchema = schema(
                new Column(0, "json1", JSON),
                new Column(1, "json2", JSON));
        JsonVisitor subject = jsonVisitor(task, inputSchema);

        // {"k1":[{"k1":"v"},"v"],"k2":["v","v"]}
        Value k1 = ValueFactory.newString("k1");
        Value k2 = ValueFactory.newString("k2");
        Value v = ValueFactory.newString("v");
        Value map = ValueFactory.newMap(
                k1, ValueFactory.newArray(ValueFactory.newMap(k1, v), v),
                k2, ValueFactory.newArray(v, v));

        MapValue visited = subject.visit("$.json1", map).asMapValue();
        assertEquals("{\"k1\":[{\"k1\":\"v\"}],\"k3\":[{\"k3\":\"v\"}]}", visited.toString());
    }
}