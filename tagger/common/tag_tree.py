"""Navigate the ``tag_tree.json`` tree exported from ``BelarusianTags``/``TagLetter``.

The JSON has the following recursive shape (see
``compiler/.../ExportToTagger.java``)::

    {
      "children": {
        "N": {"group": "Часціна мовы", "desc": "назоўнік", "children": {...}},
        "V": {"group": "Часціна мовы", "desc": "дзеяслоў", "children": {...}},
        ...
      }
    }

Every node (the root, and every ``children[letter]`` entry) has the same
shape: a dict with a ``"children"`` key mapping the next possible letter to
the child entry. A ``children[letter]`` entry additionally carries
``"group"``/``"desc"`` describing what choosing that particular letter means
*at this position of the tag*. All sibling letters at the same node share
the same ``group`` name (they are alternative values of the same category),
so ``group_at(node)`` below just reads it off the first child.

This module is the runtime equivalent of ``TagLetter.next(c)`` /
``getLetterInfo`` used in ``reader/src/main/java/.../BelarusianTags.java``,
re-implemented on top of the exported JSON so Python scripts never need to
call into Java.
"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional


Node = Dict[str, object]


@dataclass
class TagPosition:
    """One resolved position while walking a tag string through the tree."""

    index: int
    prefix: str  # tag[:index] -- all letters strictly before this position
    letter: str  # the actual letter at this position (tag[index])
    group: str  # category name for this position (e.g. "Род", "Скланенне")
    desc: str  # human-readable description of `letter` within `group`
    allowed: List[str]  # all letters valid at this position (siblings incl. `letter`)


class TagTree:
    """Loads and navigates ``tag_tree.json``."""

    def __init__(self, root: Node):
        self.root: Node = root

    @classmethod
    def load(cls, path: str | Path) -> "TagTree":
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        return cls(data)

    @staticmethod
    def children_of(node: Node) -> Dict[str, Node]:
        return node.get("children", {})  # type: ignore[return-value]

    @classmethod
    def allowed_letters(cls, node: Node) -> List[str]:
        return list(cls.children_of(node).keys())

    @classmethod
    def is_terminal(cls, node: Node) -> bool:
        """True if no more letters can follow at this node (tag ends here)."""
        return len(cls.children_of(node)) == 0

    @classmethod
    def group_at(cls, node: Node) -> Optional[str]:
        """Category name decided at `node` (None if `node` is terminal)."""
        children = cls.children_of(node)
        if not children:
            return None
        return next(iter(children.values()))["group"]  # type: ignore[index]

    @classmethod
    def step(cls, node: Node, letter: str) -> Node:
        """Advance to the child node reached by consuming `letter`."""
        children = cls.children_of(node)
        if letter not in children:
            raise KeyError(
                f"Letter {letter!r} is not a valid choice here; allowed: {sorted(children)}"
            )
        return children[letter]  # type: ignore[return-value]

    def walk(self, tag: str) -> List[TagPosition]:
        """Walk a *complete, valid* tag through the tree, returning per-position info.

        Raises ``ValueError`` if the tag is not a valid path through the tree
        (e.g. contains 'X' or an impossible letter combination).
        """
        node = self.root
        positions: List[TagPosition] = []
        for i, ch in enumerate(tag):
            children = self.children_of(node)
            if ch not in children:
                raise ValueError(
                    f"Invalid tag {tag!r} at position {i} ('{ch}'): allowed {sorted(children)}"
                )
            entry = children[ch]
            positions.append(
                TagPosition(
                    index=i,
                    prefix=tag[:i],
                    letter=ch,
                    group=entry["group"],  # type: ignore[index]
                    desc=entry["desc"],  # type: ignore[index]
                    allowed=list(children.keys()),
                )
            )
            node = entry
        return positions

    def node_after(self, prefix: str) -> Node:
        """Return the tree node reached after consuming `prefix`."""
        node = self.root
        for ch in prefix:
            node = self.step(node, ch)
        return node

    def all_group_names(self) -> List[str]:
        """All distinct group names anywhere in the tree (for baseline: one model/group)."""
        groups: set[str] = set()

        def visit(node: Node) -> None:
            for entry in self.children_of(node).values():
                groups.add(entry["group"])  # type: ignore[index]
                visit(entry)

        visit(self.root)
        return sorted(groups)

    def alphabet(self) -> List[str]:
        """All distinct letters used anywhere in the tree (tag-letter vocabulary)."""
        letters: set[str] = set()

        def visit(node: Node) -> None:
            for letter, entry in self.children_of(node).items():
                letters.add(letter)
                visit(entry)

        visit(self.root)
        return sorted(letters)

    def max_depth(self) -> int:
        """Longest possible full tag (in letters) anywhere in the tree."""

        def depth(node: Node) -> int:
            children = self.children_of(node)
            if not children:
                return 0
            return 1 + max(depth(entry) for entry in children.values())

        return depth(self.root)


def iter_positions(tag: str, tree: TagTree) -> Iterable[TagPosition]:
    """Convenience re-export: same as ``tree.walk(tag)``."""
    return tree.walk(tag)

