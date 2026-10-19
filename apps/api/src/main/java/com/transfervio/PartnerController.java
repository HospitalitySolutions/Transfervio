package com.transfervio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class PartnerController {
  final Store store;
  final AuthService auth;
  final Scopes scope;
  final RuntimeConfig config;

  PartnerController(Store store, AuthService auth, Scopes scope, RuntimeConfig config) {
    this.store = store;
    this.auth = auth;
    this.scope = scope;
    this.config = config;
  }

  record Login(@NotBlank @Email String email, @NotBlank @Size(max = 200) String password) {}

  record Tenant(
      @NotBlank @Size(max = 100) String external_tenant_id,
      @NotBlank @Size(max = 160) String name) {}

  record Property(
      @NotBlank @Size(max = 100) String external_property_id,
      @NotBlank @Size(max = 160) String name,
      @NotBlank String timezone) {}

  record Invitation(
      @NotNull UUID tenant_id,
      UUID property_id,
      @NotEmpty List<String> allowed_entities,
      @Min(1) @Max(720) int expires_in_hours,
      Map<String, List<String>> export_fields) {}

  record Branding(
      @NotBlank @Size(max = 160) String brand_name,
      @Pattern(regexp = "#[0-9a-fA-F]{6}") String primary_color,
      @NotBlank @Size(max = 500) String support_text,
      @Size(max = 180000) String logo_data) {}

  record Key(@NotBlank @Size(max = 100) String label, Map<String, List<String>> export_fields) {}

  record Catalog(
      @NotNull UUID property_id, @NotEmpty @Size(max = 1000) List<@Valid CatalogItem> entries) {}

  record CatalogItem(
      @Pattern(regexp = "SERVICE|EMPLOYEE") String kind,
      @NotBlank @Size(max = 100) String external_id,
      @NotBlank @Size(max = 160) String name) {}

  @PostMapping("/auth/login")
  Map<String, Object> login(@Valid @RequestBody Login req) {
    return auth.login(req.email(), req.password());
  }

  @PostMapping("/v1/logout")
  void logout(@AuthenticationPrincipal AuthService.Identity who) {
    store.db.update(
        "update credentials set revoked=true where id=? and partner_id=?",
        who.credentialId(),
        who.partnerId());
  }

  @GetMapping("/v1/me")
  Map<String, Object> me(@AuthenticationPrincipal AuthService.Identity who) {
    return store.one(
        "select id,name,brand_name,primary_color,support_text,logo_data from partners where id=?",
        who.partnerId());
  }

  @PutMapping("/v1/branding")
  void brand(@AuthenticationPrincipal AuthService.Identity who, @Valid @RequestBody Branding b) {
    who.admin();
    String logo = b.logo_data() == null ? "" : b.logo_data();
    if (!logo.isBlank() && !logo.matches("data:image/(png|jpeg);base64,[A-Za-z0-9+/=]+"))
      throw Store.error(400, "Logo must be an embedded PNG or JPEG");
    store.db.update(
        "update partners set brand_name=?,primary_color=?,support_text=?,logo_data=? where id=?",
        b.brand_name(),
        b.primary_color(),
        b.support_text(),
        logo,
        who.partnerId());
  }

  @GetMapping("/v1/api-keys")
  List<Map<String, Object>> keys(@AuthenticationPrincipal AuthService.Identity who) {
    who.admin();
    return store.rows(
        "select id,label,created_at,expires_at,revoked,export_fields from credentials where"
            + " partner_id=? and kind='API_KEY' order by created_at desc",
        who.partnerId());
  }

  @PostMapping("/v1/api-keys")
  Map<String, Object> key(
      @AuthenticationPrincipal AuthService.Identity who, @Valid @RequestBody Key req) {
    who.admin();
    return auth.issue(who.partnerId(), req.label(), "API_KEY", req.export_fields());
  }

  @PostMapping("/v1/api-keys/{id}/revoke")
  void revokeKey(@AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    who.admin();
    if (store.db.update(
            "update credentials set revoked=true where id=? and partner_id=? and kind='API_KEY'",
            id,
            who.partnerId())
        == 0) throw Store.error(404, "Key not found");
  }

  @GetMapping("/v1/tenants")
  List<Map<String, Object>> tenants(@AuthenticationPrincipal AuthService.Identity who) {
    return store.rows(
        "select * from tenants where partner_id=? order by created_at desc", who.partnerId());
  }

  @PostMapping("/v1/tenants")
  Map<String, Object> tenant(
      @AuthenticationPrincipal AuthService.Identity who, @Valid @RequestBody Tenant req) {
    return store.one(
        "insert into tenants(id,partner_id,external_tenant_id,name) values (?,?,?,?) on"
            + " conflict(partner_id,external_tenant_id) do update set name=excluded.name returning"
            + " *",
        UUID.randomUUID(),
        who.partnerId(),
        req.external_tenant_id(),
        req.name());
  }

  @GetMapping("/v1/tenants/{id}/properties")
  List<Map<String, Object>> properties(
      @AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    scope.tenant(who.partnerId(), id);
    return store.rows(
        "select * from properties where tenant_id=? and partner_id=? order by name",
        id,
        who.partnerId());
  }

  @DeleteMapping("/v1/tenants/{id}")
  @Transactional
  void deleteTenant(@AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    who.admin();
    store.one("select id from tenants where id=? and partner_id=? for update", id, who.partnerId());
    if (store.db.queryForObject(
            "select count(*) from migrations where tenant_id=?", Integer.class, id)
        > 0)
      throw Store.error(409, "Delete this customer's migrations before deleting the customer");
    store.db.update("delete from tenants where id=? and partner_id=?", id, who.partnerId());
    store.audit(who.partnerId(), null, "tenant.deleted");
  }

  @DeleteMapping("/v1/tenants/{id}/properties/{propertyId}")
  @Transactional
  void deleteProperty(
      @AuthenticationPrincipal AuthService.Identity who,
      @PathVariable UUID id,
      @PathVariable UUID propertyId) {
    who.admin();
    store.one(
        "select id from properties where id=? and tenant_id=? and partner_id=? for update",
        propertyId,
        id,
        who.partnerId());
    if (store.db.queryForObject(
            "select count(*) from migrations where property_id=?", Integer.class, propertyId)
        > 0)
      throw Store.error(409, "Delete this location's migrations before deleting the location");
    store.db.update(
        "delete from properties where id=? and tenant_id=? and partner_id=?",
        propertyId,
        id,
        who.partnerId());
    store.audit(who.partnerId(), null, "property.deleted");
  }

  @PostMapping("/v1/tenants/{id}/properties")
  Map<String, Object> property(
      @AuthenticationPrincipal AuthService.Identity who,
      @PathVariable UUID id,
      @Valid @RequestBody Property req) {
    scope.tenant(who.partnerId(), id);
    ZoneId.of(req.timezone());
    return store.one(
        "insert into properties(id,tenant_id,partner_id,external_property_id,name,timezone) values"
            + " (?,?,?,?,?,?) on conflict(tenant_id,external_property_id) do update set"
            + " name=excluded.name,timezone=excluded.timezone returning *",
        UUID.randomUUID(),
        id,
        who.partnerId(),
        req.external_property_id(),
        req.name(),
        req.timezone());
  }

  @GetMapping("/v1/tenants/{id}/catalog")
  List<Map<String, Object>> catalog(
      @AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    scope.tenant(who.partnerId(), id);
    return store.rows(
        "select * from catalog_entries where tenant_id=? and partner_id=? order by kind,name",
        id,
        who.partnerId());
  }

  @PutMapping("/v1/tenants/{id}/catalog")
  @Transactional
  void catalog(
      @AuthenticationPrincipal AuthService.Identity who,
      @PathVariable UUID id,
      @Valid @RequestBody Catalog req) {
    scope.property(who.partnerId(), id, req.property_id());
    for (var row : req.entries())
      store.db.update(
          "insert into catalog_entries(id,partner_id,tenant_id,property_id,kind,external_id,name)"
              + " values (?,?,?,?,?,?,?) on conflict(property_id,kind,external_id) do update set"
              + " name=excluded.name",
          UUID.randomUUID(),
          who.partnerId(),
          id,
          req.property_id(),
          row.kind(),
          row.external_id(),
          row.name());
  }

  @PostMapping("/v1/invitations")
  @Transactional
  Map<String, Object> invite(
      @AuthenticationPrincipal AuthService.Identity who, @Valid @RequestBody Invitation req) {
    who.fullRecords();
    var sharing = ExportSchema.policy(req.export_fields());
    scope.tenant(who.partnerId(), req.tenant_id());
    if (req.property_id() != null)
      scope.property(who.partnerId(), req.tenant_id(), req.property_id());
    if (!Set.of("CLIENT", "APPOINTMENT").containsAll(req.allowed_entities()))
      throw Store.error(400, "Only CLIENT and APPOINTMENT are supported");
    UUID migration = UUID.randomUUID(), id = UUID.randomUUID();
    String token = Store.token();
    Instant expiry = Instant.now().plusSeconds(req.expires_in_hours() * 3600L);
    store.db.update(
        "insert into migrations(id,partner_id,tenant_id,property_id,export_fields) values"
            + " (?,?,?,?,?::jsonb)",
        migration,
        who.partnerId(),
        req.tenant_id(),
        req.property_id(),
        store.json(sharing));
    store.db.update(
        "insert into invitations(id,migration_id,token_hash,allowed_entities,expires_at) values"
            + " (?,?,?,?::text[],?)",
        id,
        migration,
        Store.hash(token),
        "{" + String.join(",", req.allowed_entities()) + "}",
        java.sql.Timestamp.from(expiry));
    store.audit(who.partnerId(), migration, "invitation.created");
    return Map.of(
        "invitation_id",
        id,
        "migration_id",
        migration,
        "upload_url",
        config.webUrl + "/i/" + token,
        "expires_at",
        expiry.toString());
  }

  @GetMapping("/v1/invitations")
  List<Map<String, Object>> invites(@AuthenticationPrincipal AuthService.Identity who) {
    return store.rows(
        "select"
            + " i.id,i.migration_id,i.expires_at,i.revoked,i.allowed_entities,m.tenant_id,m.property_id,t.name"
            + " tenant_name,p.name property_name,m.status from invitations i join migrations m on"
            + " m.id=i.migration_id join tenants t on t.id=m.tenant_id left join properties p on"
            + " p.id=m.property_id where m.partner_id=? order by i.created_at desc",
        who.partnerId());
  }

  @PostMapping("/v1/invitations/{id}/revoke")
  @Transactional
  void revoke(@AuthenticationPrincipal AuthService.Identity who, @PathVariable UUID id) {
    var row =
        store.one(
            "select m.id from migrations m join invitations i on i.migration_id=m.id where i.id=?"
                + " and m.partner_id=? for update of m,i",
            id,
            who.partnerId());
    store.db.update("update invitations set revoked=true where id=?", id);
    store.audit(who.partnerId(), Store.id(row.get("id")), "invitation.revoked");
  }

  @GetMapping("/v1/migrations")
  List<Map<String, Object>> migrations(@AuthenticationPrincipal AuthService.Identity who) {
    return store.rows(
        "select m.*,t.name tenant_name,p.name property_name from migrations m join tenants t on"
            + " t.id=m.tenant_id left join properties p on p.id=m.property_id where m.partner_id=?"
            + " order by m.created_at desc",
        who.partnerId());
  }

  @GetMapping("/public/invitations/{token}")
  Map<String, Object> landing(@PathVariable String token) {
    var m = scope.invitation(token, false);
    var partner =
        store.one(
            "select brand_name,primary_color,support_text,logo_data from partners where id=?",
            m.get("partner_id"));
    var out = new LinkedHashMap<String, Object>();
    out.put("brand", partner);
    out.put("migration", m);
    out.put(
        "tenant",
        scope.tenant(Store.id(m.get("partner_id")), Store.id(m.get("tenant_id"))).get("name"));
    out.put("ai_provider", config.aiProvider);
    out.put("external_ai_enabled", config.externalAi);
    out.put("ai_synthetic_only", config.syntheticOnly);
    out.put("export_schema", ExportSchema.ENTITIES);
    out.put(
        "property",
        m.get("property_id") == null
            ? "All locations (clients only)"
            : scope
                .property(
                    Store.id(m.get("partner_id")),
                    Store.id(m.get("tenant_id")),
                    Store.id(m.get("property_id")))
                .get("name"));
    out.put(
        "catalog",
        store.rows(
            "select kind,external_id,name from catalog_entries where partner_id=? and tenant_id=?"
                + " and property_id=?",
            m.get("partner_id"),
            m.get("tenant_id"),
            m.get("property_id")));
    return out;
  }
}
