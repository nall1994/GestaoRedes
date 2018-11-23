var snmp = require('net-snmp')
var http = require('http')
var express = require('express')
var pug = require('pug')
var fs = require('fs')
var jsonfile = require('jsonfile');

var app = express()
var myServer = http.createServer(app)
var myBD = '../Database/database.json'

app.get('/w3.css',(req,res)=> {
    res.writeHead(200,{'Content-Type':'text/css'})
    fs.readFile('stylesheets/w3.css',(erro,dados) => {
        if(!erro) res.write(dados)
        else console.log('erro')
        res.end()
    })
})

app.get('/',(req,res) => {
    jsonfile.readFile(myBD,(erro,dados)=>{
        res.writeHead(200,{'Content-Type':'text/html'})
        if(!erro){
            res.write(pug.renderFile('homepage.pug',{historico: hist}))
        }
        else{
            res.end()
        }
    })
    res.end()
})


myServer.listen(2000,() => {
    console.log('Servidor à escuta na porta 2000...')
})
