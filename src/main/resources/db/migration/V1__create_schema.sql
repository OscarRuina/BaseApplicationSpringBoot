create table roles (id_role integer not null auto_increment, create_at_role datetime(6), update_at_role datetime(6), name_role enum ('ADMIN','USER') not null, primary key (id_role)) engine=InnoDB;
create table users (active_user bit not null, id_user integer not null auto_increment, create_at_user datetime(6), update_at_user datetime(6), first_name_user varchar(60) not null, last_name_user varchar(60) not null, email_user varchar(80) not null, password_user varchar(255) not null, primary key (id_user)) engine=InnoDB;
create table users_roles (id_role integer not null, id_user integer not null, primary key (id_role, id_user)) engine=InnoDB;
alter table roles add constraint UK_huvt67m8co70tcbe9yogs1yan unique (name_role);
alter table users add constraint UK_sgpbvva83aaeroqfb77028a38 unique (email_user);
alter table users_roles add constraint FK3avenccqsoqwrfur1hb8mpbrw foreign key (id_role) references roles (id_role);
alter table users_roles add constraint FK6ywr92flw5416dup8uc2egb83 foreign key (id_user) references users (id_user);
