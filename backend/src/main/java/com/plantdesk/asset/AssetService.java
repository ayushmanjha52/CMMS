package com.plantdesk.asset;

import com.plantdesk.asset.AssetDtos.AssetDetail;
import com.plantdesk.asset.AssetDtos.AssetRef;
import com.plantdesk.asset.AssetDtos.TreeNode;
import com.plantdesk.common.NotFoundException;
import com.plantdesk.tenancy.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssetService {

    private final AssetRepository assets;
    private final MeterReadingRepository readings;
    private final AssetCodeGenerator codes;
    private final Clock clock;

    public AssetService(AssetRepository assets, MeterReadingRepository readings, AssetCodeGenerator codes, Clock clock) {
        this.assets = assets;
        this.readings = readings;
        this.codes = codes;
        this.clock = clock;
    }

    /**
     * One SELECT for the entire tree, then an O(n) pass in memory. Rows arrive ordered by
     * tag, so a parent is always seen before its children.
     */
    @Transactional(readOnly = true)
    public List<TreeNode> tree() {
        List<Asset> all = assets.findAllForTree();
        Map<UUID, TreeNode> byId = new HashMap<>();
        List<TreeNode> roots = new ArrayList<>();
        for (Asset a : all) {
            TreeNode node = TreeNode.of(a);
            byId.put(a.getId(), node);
            TreeNode parent = a.getParentId() == null ? null : byId.get(a.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public AssetDetail get(UUID id) {
        Asset asset = load(id);
        // Ancestors are the tag's prefixes: PLT-01, PLT-01/AREA-03, ... — one IN query.
        String[] segments = asset.getTag().split("/");
        List<String> ancestorTags = new ArrayList<>();
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < segments.length - 1; i++) {
            if (i > 0) prefix.append('/');
            prefix.append(segments[i]);
            ancestorTags.add(prefix.toString());
        }
        List<AssetRef> path = ancestorTags.isEmpty() ? List.of() : assets.findByTagIn(ancestorTags).stream()
                .sorted((x, y) -> Integer.compare(x.getTag().length(), y.getTag().length()))
                .map(AssetRef::of).toList();
        List<AssetRef> children = assets.findByParentIdOrderByTag(id).stream().map(AssetRef::of).toList();
        return AssetDetail.of(asset, path, children);
    }

    @Transactional(readOnly = true)
    public List<AssetRef> search(String q) {
        return assets.search(q == null ? "" : q.trim(), PageRequest.of(0, 25)).stream().map(AssetRef::of).toList();
    }

    @Transactional
    public AssetDetail create(AssetDtos.CreateAssetRequest req) {
        Asset parent = req.parentId() == null ? null : load(req.parentId());
        if (parent == null && req.level() != AssetLevel.PLANT) {
            throw new IllegalArgumentException("Only a PLANT can be created without a parent");
        }
        if (parent != null && !parent.getLevel().canParent(req.level())) {
            throw new IllegalArgumentException("A " + req.level() + " cannot be placed under a " + parent.getLevel());
        }
        String code = codes.next(req.level(), req.typePrefix());
        Asset asset = new Asset(parent, req.level(), code, req.name(), clock.instant());
        asset.updateNameplate(req.name(), req.make(), req.model(), req.rating(), req.serialNumber(),
                req.criticality() == null ? Criticality.B : req.criticality(), req.commissionedOn(), true);
        assets.save(asset);
        return get(asset.getId());
    }

    @Transactional
    public AssetDetail update(UUID id, AssetDtos.UpdateAssetRequest req) {
        Asset asset = load(id);
        asset.updateNameplate(req.name(), req.make(), req.model(), req.rating(), req.serialNumber(),
                req.criticality(), req.commissionedOn(), req.inService());
        return get(id);
    }

    @Transactional
    public AssetDetail recordMeterReading(UUID id, AssetDtos.MeterReadingRequest req) {
        Asset asset = load(id);
        Instant now = clock.instant();
        long minutes = req.toMinutes();
        asset.recordRunningMinutes(minutes, now);
        readings.save(new MeterReading(id, minutes, now, TenantContext.require().userId()));
        return get(id);
    }

    @Transactional(readOnly = true)
    public Asset load(UUID id) {
        return assets.findById(id).orElseThrow(() -> new NotFoundException("Asset", id));
    }
}
