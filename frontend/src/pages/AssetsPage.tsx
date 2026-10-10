import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { TreeNode } from '../api/types';
import { AssetTag, CriticalityMark } from '../components/marks';
import { useTitle } from '../components/toast';
import { Empty, ErrorPlate, Loading, PageTitle, Plate } from '../components/ui';

export function AssetsPage() {
  useTitle('Asset register');
  const tree = useQuery({ queryKey: ['asset-tree'], queryFn: () => api<TreeNode[]>('/api/assets/tree') });
  const [filter, setFilter] = useState('');
  // Structural levels open by default; machines and components collapse under them.
  const [closed, setClosed] = useState<Set<string>>(new Set());

  const visible = useMemo(() => {
    if (!tree.data) return [];
    const f = filter.trim().toLowerCase();
    if (!f) return tree.data;
    const prune = (nodes: TreeNode[]): TreeNode[] =>
      nodes.flatMap((n) => {
        const kids = prune(n.children);
        return n.tag.toLowerCase().includes(f) || n.name.toLowerCase().includes(f) || kids.length ? [{ ...n, children: kids }] : [];
      });
    return prune(tree.data);
  }, [tree.data, filter]);

  const toggle = (id: string) =>
    setClosed((s) => {
      const next = new Set(s);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <>
      <PageTitle right={<input className="field w-64 data" placeholder="Filter by tag or name" value={filter} onChange={(e) => setFilter(e.target.value)} />}>
        Asset register
      </PageTitle>
      <ErrorPlate error={tree.error} />
      <Plate title="Plant → Area → Line → Machine → Component" right={<span className="stencil text-[11px]">Crit</span>}>
        {tree.isLoading ? <Loading /> : visible.length === 0 ? <Empty>No assets match.</Empty> : (
          <ul role="tree">
            {visible.map((n) => (
              <Node key={n.id} node={n} depth={0} closed={closed} toggle={toggle} forceOpen={!!filter} />
            ))}
          </ul>
        )}
      </Plate>
    </>
  );
}

function Node({ node, depth, closed, toggle, forceOpen }: {
  node: TreeNode;
  depth: number;
  closed: Set<string>;
  toggle: (id: string) => void;
  forceOpen: boolean;
}) {
  const open = forceOpen || !closed.has(node.id);
  const hasKids = node.children.length > 0;
  return (
    <li role="treeitem" aria-expanded={hasKids ? open : undefined}>
      <div className="flex items-center gap-2 h-10 pr-5 border-b border-engrave/70 hover:bg-white/[0.03] transition-colors" style={{ paddingLeft: 16 + depth * 22 }}>
        <button
          className={`w-4 data text-muted ${hasKids ? 'hover:text-label' : 'invisible'}`}
          onClick={() => toggle(node.id)}
          aria-label={open ? 'Collapse' : 'Expand'}
        >
          {open ? '−' : '+'}
        </button>
        <AssetTag id={node.id} tag={node.code} />
        <Link to={`/assets/${node.id}`} className={`truncate hover:underline ${node.inService ? '' : 'text-muted line-through'}`}>
          {node.name}
        </Link>
        <span className="stencil text-[11px] text-muted">{node.level}</span>
        <span className="ml-auto">
          <CriticalityMark c={node.criticality} />
        </span>
      </div>
      {hasKids && open && (
        <ul role="group">
          {node.children.map((c) => (
            <Node key={c.id} node={c} depth={depth + 1} closed={closed} toggle={toggle} forceOpen={forceOpen} />
          ))}
        </ul>
      )}
    </li>
  );
}
