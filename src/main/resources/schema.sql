DROP TABLE IF EXISTS cities;
DROP TABLE IF EXISTS countries;


CREATE TABLE countries(
	id int primary key auto_increment,
    name varchar(100) not null,
    alpha2code varchar(3),
    alpha3code varchar(4) not null unique,
    calling_code varchar(10)
);

CREATE TABLE cities (
	id int primary key auto_increment,
    name varchar(100) not null,
    lat double,
    lng double,
    country_id int,
    constraint foreign key (country_id) REFERENCES countries(id)
);


