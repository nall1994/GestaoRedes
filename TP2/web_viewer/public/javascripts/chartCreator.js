$(() => {
    $('#drawChart').click(e => {
        e.preventDefault()
        ajaxPost()
    })

    function ajaxPost() {
        var chartZone = document.getElementById('chart')
        var form = document.getElementById('chartForm')
        var formData = new FormData(form)
        $.ajax({
            type:'POST',
            contentType: false,
            url: 'http://localhost:3000/get_chart_data',
            data: formData,
            processData: false,
            success: dados => drawCanvas(JSON.parse(JSON.stringify(dados))),
            error: e => alert('Erro ao desenhar gráfico: ' + JSON.stringify(e))
        })
        $('#ipAgente').val('')
        $('#portaAgente').val('')
        $('#data').val('')
        $('#ifIndex').val('')
    }

    function drawCanvas(dados) {
        if($('#tipoInfo').val() == '1') {
            new Chart(document.getElementById('chart'), {
                type: 'line',
                data: {
                    labels: dados.labels,
                    datasets: [dados.resultado]
                },
                options: {
                    title:{
                        display:true,
                        text: 'Histórico de diferença de octetos para a interface ' + dados.ifDescr
                    }
                }
            })
        } else {
            new Chart(document.getElementById('chart'), {
                type: 'bar',
                data: {
                    labels: dados.labels,
                    datasets : [
                        {
                            label: dados.label,
                            data: dados.resultados
                        }
                    ]
                },
                options: {
                    legend: {display: false},
                    title: {
                        display:true,
                        text: 'Gráfico de informação do agente ' + dados.agente
                    },
                    scales: {
                        yAxes: [{
                            ticks: {
                                beginAtZero: true
                            }
                        }]
                    }
                }
            })
        }
            
    }
        
})