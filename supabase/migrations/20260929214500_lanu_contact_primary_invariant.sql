-- Preserve the CRM invariant that one customer can have at most one primary contact.
-- Repository queueing serializes the previous-primary clear before the new-primary write;
-- this database constraint protects the same rule against concurrent devices/clients.
create unique index if not exists lanu_crm_contacts_one_primary_per_customer_idx
  on public.lanu_crm_contacts(customer_id)
  where is_primary;
