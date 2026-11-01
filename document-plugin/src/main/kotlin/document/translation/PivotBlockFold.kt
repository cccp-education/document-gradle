package document.translation

/**
 * XF-FP2-7 — generalized fold over the [PivotBlock] hierarchy.
 *
 * One handler per subtype, exhaustive `when` with no `else`: a new block subtype
 * becomes a compile error here instead of silently falling into a catch-all
 * branch. Each handler receives its concrete block, so a consumer reads the
 * fields it needs (and keeps the node when it has to copy or re-dispatch it).
 *
 * Recursion is deliberately **not** baked in: composite blocks
 * ([PivotBlock.Admonition], inline containers) hand their children to the caller,
 * which decides how to traverse them (map, flatten, checksum, ...). The fold only
 * removes the repeated dispatch, it never imposes a traversal policy.
 */
internal fun <R> PivotBlock.fold(
    heading: (PivotBlock.Heading) -> R,
    paragraph: (PivotBlock.Paragraph) -> R,
    listBlock: (PivotBlock.ListBlock) -> R,
    table: (PivotBlock.Table) -> R,
    admonition: (PivotBlock.Admonition) -> R,
    source: (PivotBlock.Source) -> R,
    descriptionList: (PivotBlock.DescriptionList) -> R,
    blockMacro: (PivotBlock.BlockMacro) -> R,
    hr: (PivotBlock.Hr) -> R,
): R =
    when (this) {
        is PivotBlock.Heading -> heading(this)
        is PivotBlock.Paragraph -> paragraph(this)
        is PivotBlock.ListBlock -> listBlock(this)
        is PivotBlock.Table -> table(this)
        is PivotBlock.Admonition -> admonition(this)
        is PivotBlock.Source -> source(this)
        is PivotBlock.DescriptionList -> descriptionList(this)
        is PivotBlock.BlockMacro -> blockMacro(this)
        is PivotBlock.Hr -> hr(this)
    }
