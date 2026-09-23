package com.earthpol.earthpollib.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReloadableConfigNodeTest {

    @Test
    void scalarNodeStoresAndUpdatesTypedValues() {
        ReloadableConfigNode<Integer> node = ReloadableConfigNode.of("value", Integer.class, 4);

        node.setValue(8);

        assertEquals(8, node.getValue());
        assertEquals(4, node.getDefaultValue());
    }

    @Test
    void scalarNodeRejectsWrongRuntimeType() {
        @SuppressWarnings("rawtypes")
        ReloadableConfigNode rawNode = ReloadableConfigNode.of("value", Integer.class, 4);

        assertThrows(IllegalArgumentException.class, () -> rawNode.setValue("bad"));
    }

    @Test
    void listNodeCopiesAndValidatesElements() {
        ReloadableListNode<String> node = ReloadableListNode.ofList("list", String.class, List.of("a"));

        node.setValue(List.of("b", "c"));

        assertEquals(List.of("b", "c"), node.getValue());
    }

    @Test
    void listNodeRejectsWrongElementType() {
        @SuppressWarnings("rawtypes")
        ReloadableListNode rawNode = ReloadableListNode.ofList("list", String.class, List.of("a"));

        assertThrows(IllegalArgumentException.class, () -> rawNode.setValue(List.of(1)));
    }
}
