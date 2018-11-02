var snmp = require('net-snmp')
var http = require('http')
var express = require('express')
var pug = require('pug')
var fs = require('fs')

var app = express()

app.get('/w3.css',(req,res)=> {
    res.writeHead(200,{'Content-Type':'text/css'})
    fs.readFile('stylesheets/w3.css',(erro,dados) => {
        if(!erro) res.write(dados)
        else console.log('erro')
        res.end()
    })
})

app.get('/',(req,res) => {
    var session = snmp.createSession('localhost','public')
    var returnValues = {ifDescr:"",PhysAddress:"",inOctets:"",outOctets:"",diferenca:""}
    getSnmpVariables(session,returnValues)
    setTimeout(writeHTML, 100,res,returnValues);
    
    

    console.log(returnValues)

})

function writeHTML(res,returnValues) {
    returnValues['diferenca'] = (parseInt(returnValues['inOctets']) - parseInt(returnValues['outOctets'])).toString()
    res.writeHead(200,{'Content-Type': 'text/html; charset=utf-8'})
    res.write(pug.renderFile('homepage.pug',{valores: returnValues}))
    res.end() 
}

function getSnmpVariables(session, returnValues) {
    var oid = ["1.3.6.1.2.1.2.2.1.2.3"]
    session.get(oid, (error,varbinds) => {
        if(error) console.log('Fail: ' + error)
        else {
            returnValues['ifDescr'] = varbinds[0].value.toString()
            console.log(JSON.stringify(varbinds[0]))
        }
    } )

    oid = ["1.3.6.1.2.1.2.2.1.6.3"]
    session.get(oid, (error,varbinds) => {
        if(error) console.log('Fail: ' + error)
        else {
            var data = JSON.parse(JSON.stringify(varbinds[0].value))
            var str = ''
            for(var i = 0; i < data.data.length; i++) {
                if(i == (data.data.length - 1)) {
                    str += data.data[i].toString(16)
                } else {
                    str += data.data[i].toString(16) + ':' 
                }   
            }
            console.log(str)
            returnValues['PhysAddress'] = str
        }
    } )

    oid = ["1.3.6.1.2.1.2.2.1.10.3"]
    session.get(oid, (error,varbinds) => {
        if(error) console.log('Fail: ' + error)
        else {
            returnValues['inOctets'] = varbinds[0].value.toString()
            console.log(varbinds[0].value.toString())      
        }
    } )

    oid = ["1.3.6.1.2.1.2.2.1.16.3"]
    session.get(oid, (error,varbinds) => {
        if(error) console.log('Fail: ' + error)
        else {
            returnValues['outOctets'] = varbinds[0].value.toString()
            console.log(varbinds[0].value.toString())       
        }
    } )
    
}

var myServer = http.createServer(app)

myServer.listen(2000,() => {
    console.log('Servidor à escuta na porta 2000...')
})
